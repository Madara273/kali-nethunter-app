package com.offsec.nethunter;

final class KernelFlashState {
    enum Status {
        IDLE,
        SELECTED,
        FLASHING,
        SUCCESS,
        FAILURE
    }

    private volatile Status status = Status.IDLE;

    Status getStatus() {
        return status;
    }

    void select() {
        status = Status.SELECTED;
    }

    void startFlashing() {
        status = Status.FLASHING;
    }

    void complete(boolean success) {
        status = success ? Status.SUCCESS : Status.FAILURE;
    }

    void reset() {
        status = Status.IDLE;
    }
}
