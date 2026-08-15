package com.mudassirshahzad.eka.application.shared;

/**
 * Thrown for any login failure — unknown email, wrong password, inactive account, or unknown
 * tenant — always with the same generic message. Distinguishing these in the response would let a
 * caller enumerate which emails/tenants exist; the account-status/credential mismatch is
 * deliberately indistinguishable from the outside (see {@code AuthenticateUserUseCase}).
 */
public class InvalidCredentialsException extends ApplicationException {

    public InvalidCredentialsException() {
        super("Invalid email, password, or tenant");
    }
}
