package com.library.contract.closing;

public interface WriteAdmissionGate {
    boolean acquire(String canonicalPath);
}
