package com.mudassir.eka.domain.conversation;

public enum ChatSessionStatus {

    /** Session is open and accepting new turns. */
    ACTIVE,

    /** Session was closed normally by the user or application logic. */
    COMPLETED,

    /** Session expired due to inactivity before being explicitly closed. */
    TIMED_OUT
}
