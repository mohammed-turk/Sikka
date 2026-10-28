package com.example.sikka.Advice;
import com.example.sikka.Api.ApiException;
import com.example.sikka.Api.ApiResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@org.springframework.web.bind.annotation.ControllerAdvice
public class ControllerAdvice {

    @ExceptionHandler(value = ApiException.class)
    public ResponseEntity<?> ApiException(ApiException e){
        return ResponseEntity.status(400).body( new ApiResponse( e.getMessage() ) );
    }

    //handle the server side validation
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException e){
        return ResponseEntity.status(400).body(new ApiResponse(e.getBindingResult().getFieldError().getDefaultMessage()));
    }

    // handle if the entered id while he should not
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<?> handleWritingId(ObjectOptimisticLockingFailureException e){
        return ResponseEntity.status(400).body(new ApiResponse(e.getMessage()));
    }

    //handle the database side constrain SQL
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> handleDataIntegrity(DataIntegrityViolationException e){
        return ResponseEntity.status(400).body(new ApiResponse(e.getMessage()));
    }

    //for invalid type
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<?> handleTypeMismatch(MethodArgumentTypeMismatchException e){
        return ResponseEntity.status(400).body(new ApiResponse("Invalid value for " + e.getName()));
    }

    //for invalid type for mapping
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<?> handleTypeMismatch(HttpRequestMethodNotSupportedException e){
        return ResponseEntity.status(400).body(new ApiResponse( e.getMessage() ));
    }

    //for invalid type for mapping
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> handleTypeMismatch(NoResourceFoundException e){
        return ResponseEntity.status(400).body(new ApiResponse( e.getMessage() ));
    }

    //Handle invalid database queries and SQL syntax errors
    @ExceptionHandler(InvalidDataAccessResourceUsageException.class)
    public ResponseEntity<ApiResponse> handleInvalidDataAccessResourceUsageException(InvalidDataAccessResourceUsageException exception) {
        return ResponseEntity.status(500).body(new ApiResponse("A database error occurred. Please try again later"));
    }

    // Handle JPA persistence errors
    @ExceptionHandler(JpaSystemException.class)
    public ResponseEntity<ApiResponse> handleJpaSystemException(JpaSystemException exception) {
        return ResponseEntity.status(500).body(new ApiResponse("A database error occurred. Please try again later"));
    }

}