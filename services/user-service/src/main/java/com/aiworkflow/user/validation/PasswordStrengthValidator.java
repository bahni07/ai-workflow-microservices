package com.aiworkflow.user.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordStrengthValidator implements ConstraintValidator<ValidPassword, String> {

    private static final String UPPERCASE = ".*[A-Z].*";
    private static final String LOWERCASE = ".*[a-z].*";
    private static final String DIGIT     = ".*[0-9].*";
    private static final String SPECIAL   = ".*[^A-Za-z0-9].*";

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) {
            return true; // @NotBlank handles null/blank separately
        }
        return password.matches(UPPERCASE)
            && password.matches(LOWERCASE)
            && password.matches(DIGIT)
            && password.matches(SPECIAL);
    }
}
