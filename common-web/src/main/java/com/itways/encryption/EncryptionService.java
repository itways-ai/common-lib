package com.itways.encryption;

public interface EncryptionService {
    String encrypt(String data);

    String decrypt(String encryptedData);

}
