package com.itways.common.service;

public interface EncryptionService {
    String encrypt(String data);

    String decrypt(String encryptedData);

    String getPublicKey();
}
