package com.example.sikka.Service;

import com.example.sikka.Api.ApiException;
import com.example.sikka.DtoIn.AccountDtoIn;
import com.example.sikka.DtoOut.AccountDtoOut;
import com.example.sikka.Model.Account;
import com.example.sikka.Repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.example.sikka.DtoIn.RegisterDtoIn;
import com.example.sikka.Model.VisitorProfile;
import com.example.sikka.Repository.VisitorProfileRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final VisitorProfileRepository visitorProfileRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final VerificationService verificationService;
    public List<AccountDtoOut> get() {
        List<AccountDtoOut> list = new ArrayList<>();
        for (Account a : accountRepository.findAll())
            list.add(toDtoOut(a));
        return list;
    }

    public void add(AccountDtoIn dto) {
        if (accountRepository.findAccountByEmail(dto.getEmail()) != null)
            throw new ApiException("email already exists");

        Account account = new Account();
        account.setName(dto.getName());
        account.setEmail(dto.getEmail());
        account.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        account.setRole("USER");
        account.setStatus("ACTIVE");
        account.setEmailVerified(false);
        accountRepository.save(account);
    }

    public void update(Integer id, AccountDtoIn dto) {
        Account account = accountRepository.findAccountById(id);
        if (account == null)
            throw new ApiException("account not found");

        Account sameEmail = accountRepository.findAccountByEmail(dto.getEmail());
        if (sameEmail != null && !sameEmail.getId().equals(id))
            throw new ApiException("email already exists");

        account.setName(dto.getName());
        account.setEmail(dto.getEmail());
        account.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        accountRepository.save(account);
    }

    @org.springframework.transaction.annotation.Transactional
    public void delete(Integer id) {
        Account account = accountRepository.findAccountById(id);
        if (account == null)
            throw new ApiException("account not found");

        if ((account.getOrganizedVisits() != null && !account.getOrganizedVisits().isEmpty()) || (account.getOrganizedGroups() != null && !account.getOrganizedGroups().isEmpty()) || (account.getNotifications() != null && !account.getNotifications().isEmpty()) || (account.getPlanApprovals() != null && !account.getPlanApprovals().isEmpty()) || (account.getVisitRecords() != null && !account.getVisitRecords().isEmpty()) || (account.getPaymentOrders() != null && !account.getPaymentOrders().isEmpty()) || (account.getPrivacyConsents() != null && !account.getPrivacyConsents().isEmpty())) {
            throw new ApiException("Cannot delete an account linked to visits, groups, notifications, approvals, records, payments or consents");
        }
        VisitorProfile profile = account.getVisitorProfile();
        if (profile != null && ((profile.getVisitMembers() != null && !profile.getVisitMembers().isEmpty()) || (profile.getRidePreferences() != null && !profile.getRidePreferences().isEmpty()) || (profile.getPrivacyConsents() != null && !profile.getPrivacyConsents().isEmpty()))) {
            throw new ApiException("Cannot delete an account whose profile is linked to visit memberships, ride preferences or consents");
        }
        accountRepository.delete(account);
    }

    // creates the account and its visitor profile together (@Transactional: both or none)
    @Transactional
    public void register(RegisterDtoIn dto) {
        if (dto == null) {
            throw new ApiException("Profile details are required");
        }
        if (dto.getThrillPreference() != null && !dto.getThrillPreference().matches("^(LOW|MILD|EXTREME)$")) {
            throw new ApiException("Thrill preference must be LOW, MILD or EXTREME");
        }
        if (accountRepository.findAccountByEmail(dto.getEmail()) != null)
            throw new ApiException("email already exists");

        Account account = new Account();
        account.setName(dto.getName());
        account.setEmail(dto.getEmail());
        account.setPhone(dto.getPhone());
        account.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        account.setRole("USER");
        account.setStatus("ACTIVE");
        account.setEmailVerified(false);
        accountRepository.save(account);

        VisitorProfile profile = new VisitorProfile();
        profile.setName(dto.getName());
        profile.setBirthDate(dto.getBirthDate());
        profile.setHeightCm(dto.getHeightCm());
        profile.setWeightKg(dto.getWeightKg());
        profile.setGender(dto.getGender());
        profile.setThrillPreference(dto.getThrillPreference());
        profile.setPreferredRideTypes(dto.getPreferredRideTypes());
        profile.setDislikedRideFeatures(dto.getDislikedRideFeatures());
        profile.setFoodPreferences(dto.getFoodPreferences());
        profile.setAccount(account);
        visitorProfileRepository.save(profile);
        verificationService.sendVerificationCode(account);
    }

    // contract method for the other flows
    public Account getAccount(Integer accountId) {
        Account account = accountRepository.findAccountById(accountId);
        if (account == null)
            throw new ApiException("account not found");
        return account;
    }

    // the user reads his own account (without the password)
    public AccountDtoOut getMe(Integer accountId) {
        return toDtoOut(getAccount(accountId));
    }

    private AccountDtoOut toDtoOut(Account a) {
        return new AccountDtoOut(a.getId(), a.getName(), a.getEmail(), a.getRole(), a.getStatus(), a.getEmailVerified(), a.getPhoneVerified(), a.getCreatedAt());
    }
}
