package com.example.sikka.Service;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.beans.factory.annotation.Value;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import com.example.sikka.Api.ApiException;
import com.example.sikka.DtoIn.VisitGroupDtoIn;
import com.example.sikka.DtoOut.VisitGroupDtoOut;
import com.example.sikka.Model.Account;
import com.example.sikka.Model.Visit;
import com.example.sikka.Model.VisitGroup;
import com.example.sikka.Repository.AccountRepository;
import com.example.sikka.Repository.VisitGroupRepository;
import com.example.sikka.Repository.VisitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VisitGroupService {

    private final VisitGroupRepository visitGroupRepository;
    private final AccountRepository accountRepository;
    private final VisitRepository visitRepository;
    @Value("${sikka.join-url}")
    private String joinUrl;

    public List<VisitGroupDtoOut> get() {
        List<VisitGroupDtoOut> list = new ArrayList<>();
        for (VisitGroup g : visitGroupRepository.findAll())
            list.add(toDtoOut(g));
        return list;
    }

    // returns the raw invite code (shown once, only its hash is stored)
    public String add(VisitGroupDtoIn dto) {
        Account organizer = accountRepository.findAccountById(dto.getOrganizerId());
        if (organizer == null)
            throw new ApiException("organizer not found");

        String code = newCode();

        VisitGroup group = new VisitGroup();
        group.setName(dto.getName());
        group.setInvitationsEnabled(dto.getInvitationsEnabled() == null ? true : dto.getInvitationsEnabled());
        group.setOrganizer(organizer);
        group.setInviteCodeHash(hash(code));
        visitGroupRepository.save(group);
        return code;
    }

    public void update(Integer id, VisitGroupDtoIn dto) {
        VisitGroup group = visitGroupRepository.findVisitGroupById(id);
        if (group == null)
            throw new ApiException("group not found");

        group.setName(dto.getName());
        if (dto.getInvitationsEnabled() != null)
            group.setInvitationsEnabled(dto.getInvitationsEnabled());
        visitGroupRepository.save(group);
    }

    @org.springframework.transaction.annotation.Transactional
    public void delete(Integer id) {
        VisitGroup group = visitGroupRepository.findVisitGroupById(id);
        if (group == null)
            throw new ApiException("group not found");

        Visit visit = group.getVisit();
        if (visit != null) {
            visit.setGroup(null);
            group.setVisit(null);
            visitRepository.save(visit);
        }
        if (group.getOrganizer().getOrganizedGroups() != null) {
            group.getOrganizer().getOrganizedGroups().remove(group);
        }
        visitGroupRepository.delete(group);
    }

    // used by VisitMemberService to join by invite code
    public VisitGroup getGroupByInviteCode(String code) {
        VisitGroup group = visitGroupRepository.findVisitGroupByInviteCodeHash(hash(code));
        if (group == null)
            throw new ApiException("invalid invite code");
        return group;
    }

    private String hash(String code) {
        if (code == null || code.isBlank()) {
            throw new ApiException("Invite code is required");
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(code.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes)
                sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new ApiException("hashing error");
        }
    }

    private VisitGroupDtoOut toDtoOut(VisitGroup g) {
        return new VisitGroupDtoOut(g.getId(), g.getName(), g.getOrganizer().getId(), g.getInvitationsEnabled(), g.getCreatedAt());
    }
    // creates a group for an existing visit and returns the raw invite code
    @org.springframework.transaction.annotation.Transactional
    public String attachGroup(Visit visit, String groupName) {
        if (visit == null || groupName == null || groupName.isBlank() || groupName.length() > 150) {
            throw new ApiException("Visit and group name (up to 150 characters) are required");
        }
        if (visit.getGroup() != null) {
            throw new ApiException("Visit already has a group");
        }
        if ((!"DRAFT".equals(visit.getStatus()) && !"PLANNING".equals(visit.getStatus())) || Boolean.TRUE.equals(visit.getMembersLocked())) {
            throw new ApiException("Group cannot be added after planning starts or the visit closes");
        }
        String code = newCode();

        VisitGroup group = new VisitGroup();
        group.setName(groupName);
        group.setInvitationsEnabled(true);
        group.setOrganizer(visit.getOrganizer());
        group.setInviteCodeHash(hash(code));
        visitGroupRepository.save(group);

        visit.setGroup(group);
        visitRepository.save(visit);
        return code;
    }

    // for a solo visit that wants friends later
    @org.springframework.transaction.annotation.Transactional
    public String addGroupToVisit(Integer accountId, Integer visitId, String groupName) {
        Visit visit = visitRepository.findVisitByIdForUpdate(visitId);
        if (visit == null)
            throw new ApiException("visit not found");
        if (!visit.getOrganizer().getId().equals(accountId))
            throw new ApiException("only the organizer can do this");
        if (visit.getGroup() != null)
            throw new ApiException("visit already has a group");
        if (!"FRIENDS".equals(visit.getTripType()))
            throw new ApiException("only a FRIENDS trip can have a group");
        if (visit.getStatus().equals("CANCELLED") || visit.getStatus().equals("COMPLETED"))
            throw new ApiException("visit is closed");

        return attachGroup(visit, groupName);
    }

    public String regenerateCode(Integer accountId, Integer visitId) {
        VisitGroup group = getOrganizerGroup(accountId, visitId);
        String code = newCode();
        group.setInviteCodeHash(hash(code));
        visitGroupRepository.save(group);
        return code;
    }

    // checks the caller is the organizer and the code is the group's real code, then builds the join link
    public String joinLink(Integer accountId, Integer visitId, String code) {
        VisitGroup group = getOrganizerGroup(accountId, visitId);
        if (!MessageDigest.isEqual(hash(code).getBytes(StandardCharsets.UTF_8),
                group.getInviteCodeHash().getBytes(StandardCharsets.UTF_8)))
            throw new ApiException("invalid invite code");
        return joinUrl + "?code=" + URLEncoder.encode(code, StandardCharsets.UTF_8);
    }

    // QR image of the join link
    public byte[] qrCode(Integer accountId, Integer visitId, String code) {
        String link = joinLink(accountId, visitId, code);
        try {
            BitMatrix matrix = new QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, 300, 300);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new ApiException("could not create QR code");
        }
    }

    // flips invitations on/off, returns the new value
    public boolean toggleInvitations(Integer accountId, Integer visitId) {
        VisitGroup group = getOrganizerGroup(accountId, visitId);
        group.setInvitationsEnabled(!group.getInvitationsEnabled());
        visitGroupRepository.save(group);
        return group.getInvitationsEnabled();
    }

    private VisitGroup getOrganizerGroup(Integer accountId, Integer visitId) {
        Visit visit = visitRepository.findVisitById(visitId);
        if (visit == null)
            throw new ApiException("visit not found");
        if (!visit.getOrganizer().getId().equals(accountId))
            throw new ApiException("only the organizer can do this");
        if (visit.getGroup() == null)
            throw new ApiException("visit has no group");
        return visit.getGroup();
    }

    private String newCode() {
        String characters = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        java.security.SecureRandom random = new java.security.SecureRandom();

        for (int attempt = 0; attempt < 50; attempt++) {
            StringBuilder code = new StringBuilder();

            for (int index = 0; index < 5; index++) {
                code.append(characters.charAt(random.nextInt(characters.length())));
            }

            String inviteCode = code.toString();

            if (visitGroupRepository.findVisitGroupByInviteCodeHash(hash(inviteCode)) == null) {
                return inviteCode;
            }
        }

        throw new ApiException("Unable to generate an available invite code; try again");
    }

}
