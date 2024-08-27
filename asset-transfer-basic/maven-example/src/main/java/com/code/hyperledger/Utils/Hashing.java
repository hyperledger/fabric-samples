package com.code.hyperledger.Utils;

import java.security.NoSuchAlgorithmException;
import java.security.MessageDigest;

public class Hashing {
        public static String sha256(String input) {
        try {
            // Crear una instancia de MessageDigest para SHA-256
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            // Calcular el hash
            byte[] encodedhash = digest.digest(input.getBytes());

            // Convertir el hash a una representación hexadecimal
            return bytesToHex(encodedhash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
