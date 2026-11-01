package serwio.account.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import serwio.validation.ValidOwnerRegistration;

@ValidOwnerRegistration
public record OwnerRegistrationRequest(
        @NotBlank(message = "Podaj imię.") @Size(max = 127, message = "Imię może mieć najwyżej 127 znaków.") String firstName,
        @NotBlank(message = "Podaj nazwisko.") @Size(max = 127, message = "Nazwisko może mieć najwyżej 127 znaków.") String lastName,
        @NotBlank(message = "Podaj adres e-mail.") @Email(message = "Podaj poprawny adres e-mail.")
        @Size(max = 254, message = "Adres e-mail może mieć najwyżej 254 znaki.") String email,
        @NotBlank(message = "Podaj hasło.") @Size(min = 8, max = 72, message = "Hasło musi mieć od 8 do 72 znaków.") String password,
        @NotBlank(message = "Potwierdź hasło.") @Size(max = 72, message = "Hasło może mieć najwyżej 72 znaki.") String confirmPassword
) {
    public OwnerRegistrationRequest {
        firstName = firstName == null ? null : firstName.strip();
        lastName = lastName == null ? null : lastName.strip();
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "OwnerRegistrationRequest";
    }
}
