package user_interface.server.tools;

import org.apache.commons.io.FileUtils;
import seeding.Constants;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

/**
 * Created by ehallmark on 8/29/17.
 */
public class PasswordHandler {
    private static final String passwordFolder = Constants.DATA_FOLDER+"passwords/";
    public boolean authorizeUser(String username, String password) throws PasswordException {
        if(username == null || password == null) return false;
        File passwordFile = new File(passwordFolder+username);
        if(!passwordFile.exists()) return false;
        String encryptedPassword;
        try {
            encryptedPassword = FileUtils.readFileToString(passwordFile);
        } catch(Exception e) {
            throw new PasswordException("Error reading password file.");
        }
        if(encryptedPassword == null) {
            throw new PasswordException("Unable to find password.");
        }
        String actualPassword = decrypt(encryptedPassword);
        if(actualPassword == null) {
            throw new PasswordException("Unable to decrypt password.");
        }
        return actualPassword.equals(password);
    }

    public void changePassword(String username, String oldPassword, String newPassword) throws PasswordException {
        // authorize
        boolean authorized = authorizeUser(username, oldPassword);
        if(!authorized) {
            throw new PasswordException("User is not authorized.");
        }
        // validate password
        validatePassword(newPassword);
        saveUserToFile(username, newPassword);
    }

    public void createUser(String username, String password) throws PasswordException {
        // validate
        validateUsername(username);
        validatePassword(password);
        // encrypt and save
        saveUserToFile(username, password);
    }

    public void deleteUser(String username, String password) throws PasswordException {
        boolean authorized = authorizeUser(username, password);
        if(!authorized) {
            throw new PasswordException("User is not authorized.");
        }
        File fileToRemove = new File(passwordFolder+username);
        if(!fileToRemove.exists()) {
            throw new PasswordException("Unable to find user file.");
        }
        boolean deleted = fileToRemove.delete();
        if(!deleted) {
            throw new PasswordException("Unable to delete user file.");
        }
    }


    // HELPER METHODS

    private static void saveUserToFile(String username, String password) throws PasswordException{
        // encrypt
        File passwordFile = new File(passwordFolder+username);
        String encryptedPassword = encrypt(password);
        // save
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(passwordFile))) {
            writer.write(encryptedPassword);
            writer.flush();
        } catch(Exception e) {
            throw new PasswordException("Error creating password file.");
        }
    }

    private static void validateUsername(String username) throws PasswordException {
        if(username == null) {
            throw new PasswordException("Please include username.");
        }
        if(username.replaceAll("[^a-zA-Z0-9]","").length()!=username.length()) {
            throw new PasswordException("Username must be alphanumeric.");
        }
        File passwordFile = new File(passwordFolder+username);
        if(passwordFile.exists()) {
            throw new PasswordException("User already exists.");
        }
    }

    private static void validatePassword(String password) throws PasswordException {
        if(password == null) {
            throw new PasswordException("Please include password.");
        }
        if(password.length() < 6) {
            throw new PasswordException("Password must be at least 6 characters.");
        }
        if(password.replaceAll("[^a-zA-Z]","").isEmpty()) {
            throw new PasswordException("Password must contain a number.");
        }
        if(password.replaceAll("[^a-z0-9]","").isEmpty()) {
            throw new PasswordException("Password must contain an uppercase character.");
        }
    }

    private static final long p1 = 365560343L;
    private static final long p2 = 787898057L;
    private static final long n = p1 * p2;
    private static final long phi = (p1-1)*(p2-1);
    private static final long e = 65537L;
    private static final long d = greedyEuclid(e, phi);

    public static String encrypt(String password) {
        char[] chars = password.toCharArray();
        for(int i = 0; i < chars.length; i++) {
            chars[i] = Math.pow(chars[i],e
        }
    }

    public static String decrypt(String password) {

    }

    private static long greedyEuclid(long e, long phi) {
        System.out.println("Starting to compute greedy euclid...");
        long t1 = System.currentTimeMillis();
        int d = 0;
        while((e*d) % phi != 1) {
            d++;
        }
        System.out.println("Took "+ ((t1-System.currentTimeMillis())/1000) + " seconds to complete greedy euclid.");
        return d;
    }
}
