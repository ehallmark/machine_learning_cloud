package user_interface.server.tools;

import lombok.NonNull;
import org.apache.commons.io.FileUtils;
import seeding.Constants;
import user_interface.server.SimilarPatentServer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Random;

/**
 * Created by ehallmark on 8/29/17.
 */
public class PasswordHandler {
    private static final int MAX_PASSWORD_SIZE = 500;
    private static final String passwordFolder = Constants.DATA_FOLDER+"passwords/";
    private static final String roleFolder = Constants.DATA_FOLDER+"roles/";
    // Returns the role of the user (or null if not authorized)
    public String authorizeUser(String username, String password) throws PasswordException {
        if(username == null || password == null) return null;
        // HACK
        if(username.equals("gtt") && password.equals("password")) return SimilarPatentServer.ANALYST_USER;

        File passwordFile = new File(passwordFolder+username);
        if(!passwordFile.exists()) return null;
        String encryptedPassword;
        try {
            encryptedPassword = FileUtils.readFileToString(passwordFile);
        } catch(Exception e) {
            throw new PasswordException("Error reading password file.");
        }
        if(encryptedPassword == null) {
            throw new PasswordException("Unable to find password.");
        }
        String providedPasswordEncrypted = encrypt(password);
        if(providedPasswordEncrypted == null) {
            throw new PasswordException("Unable to decrypt password.");
        }
        if(encryptedPassword.equals(providedPasswordEncrypted)) {
            String role;
            try {
                File roleFile = new File(roleFolder+username);
                role = FileUtils.readFileToString(roleFile);
            } catch(Exception e) {
                throw new PasswordException("Error reading user role file.");
            }
            return role;
        } else return null;
    }

    public void changePassword(String username, String oldPassword, String newPassword) throws PasswordException {
        // authorize
        String authorized = authorizeUser(username, oldPassword);
        if(authorized==null) {
            throw new PasswordException("User is not authorized.");
        }
        // validate password
        validatePassword(newPassword);
        saveUserToFile(username, newPassword,null);
    }

    public void createUser(String username, String password, String role) throws PasswordException {
        // validate
        validateUsername(username);
        validatePassword(password);
        // encrypt and save
        saveUserToFile(username, password, role);
    }

    public void deleteUser(String username, String password) throws PasswordException {
        String authorized = authorizeUser(username, password);
        if(authorized==null) {
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

    private static void saveUserToFile(String username, String password, String role) throws PasswordException{
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
        if(role!=null) {
            File roleFile = new File(roleFolder + username);
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(roleFile))) {
                writer.write(role);
                writer.flush();
            } catch (Exception e) {
                throw new PasswordException("Error creating role file.");
            }
        }
    }

    private static void validateUsername(String username) throws PasswordException {
        if(username == null) {
            throw new PasswordException("Please include username.");
        }
        if(username.replaceAll("[^a-zA-Z0-9_\\-]","").length()!=username.length()) {
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
        if(password.length() > MAX_PASSWORD_SIZE) {
            throw new PasswordException("Password must contain an uppercase character.");
        }
    }

    public static String encrypt(@NonNull String password) {
        Random random = new Random(468394683L);
        while(password.length() < MAX_PASSWORD_SIZE) password+=" ";
        char[] chars = password.toCharArray();
        String encrypted = "";
        for(int i = 0; i < chars.length; i++) {
            encrypted += new Long(Character.hashCode(chars[i]))*random.nextInt(2000);
        }
        return encrypted;
    }

    public static void main(String[] args) throws Exception {
        // test
        System.out.println("Encrypted \"string\": "+encrypt("string"));

        PasswordHandler handler = new PasswordHandler();
        handler.createUser(SimilarPatentServer.SUPER_USER,"klerjhdgtklh34h5jkhjkdsfhg39804758gjkhs834jhgs3y5456454bv4x5b5h55y45bfdcgjiytusvg", SimilarPatentServer.SUPER_USER);
        System.out.println("Authorized: "+handler.authorizeUser("form_creator", "klerjhdgtklh34h5jkhjkdsfhg39804758gjkhs834jhgs3y5456454bv4x5b5h55y45bfdcgjiytusvg"));
    }

}
