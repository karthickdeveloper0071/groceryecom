package com.groceryecom.shared.exception;

/**
 * 402: the caller is who they say they are and may do this in principle, but the
 * licence that pays for it has run out.
 *
 * <p>Deliberately not 403. "You are not allowed" and "your plan expired on 14 March,
 * renew to carry on" are different conversations, and a client that cannot tell them
 * apart shows the wrong screen: an error page instead of a renew button.
 */
public class PaymentRequiredException extends ApplicationException {

    public PaymentRequiredException(String message, String errorCode) {
        super(message, errorCode, 402);
    }
}
