package user_interface.server.tools;

import lombok.NonNull;
import org.apache.commons.crypto.stream.CryptoOutputStream;
import org.apache.commons.io.FileUtils;
import seeding.Constants;
import seeding.Database;
import user_interface.server.BigQueryServer;
import user_interface.server.SimilarPatentServer;

import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Created by ehallmark on 8/29/17.
 */
public class PasswordHandler {
    private static final int MAX_PASSWORD_SIZE = 500;
    private static final String passwordFolder = Constants.DATA_FOLDER+"passwords/";
    private static final String roleFolder = Constants.DATA_FOLDER+"roles/";
    private static final String userGroupFolder = Constants.DATA_FOLDER+"userGroups/";
    private static final String userGroupNamesFile = Constants.DATA_FOLDER+"userGroupNames.jobj";
    public static final String USER_NAME_REGEX = "[^a-zA-Z0-9_\\-\\.]";
    // Returns the role of the user (or null if not authorized)
    public synchronized String authorizeUser(String username, String password) throws PasswordException {
        if(username == null || password == null) return null;
        //// HACK
        //if(username.equals("gtt") && password.equals("password")) return SimilarPatentServer.ANALYST_USER;

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

    public synchronized void changeUserGroup(String username, String newGroup) throws PasswordException {
        File groupFile = new File(userGroupFolder+username);
        if(newGroup==null) {
            if(groupFile.exists()) {
                groupFile.delete();
            }
        } else {
            try(BufferedWriter bw = new BufferedWriter(new FileWriter(groupFile))) {
                bw.write(newGroup);
                bw.flush();
            } catch(Exception e) {
                throw new PasswordException("Error creating new usergroup file.");
            }
        }
    }

    public synchronized String getUserGroup(String username) throws PasswordException {
        File groupFile = new File(userGroupFolder+username);
        if(groupFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(groupFile))) {
                return reader.readLine();
            } catch(Exception e) {
                throw new PasswordException("Error retrieving user group.");
            }
        } else {
            return null;
        }
    }

    public synchronized Set<String> getUserGroups() {
        File userGroupsFile = new File(userGroupNamesFile);
        if(userGroupsFile.exists()) {
            return (Set<String>) Database.tryLoadObject(userGroupsFile);
        } else {
            return Collections.synchronizedSet(new HashSet<>());
        }
    }

    public synchronized void createUserGroup(String group) throws PasswordException {
        List<String> allUsers = getAllUsers();
        if(allUsers.contains(group)) {
            throw new PasswordException("User group conflicts with an existing username.");
        }
        validateUsername(group); // important since a directory will be created
        Set<String> currentGroups = getUserGroups();
        currentGroups.add(group);
        Database.trySaveObject(currentGroups,new File(userGroupNamesFile));
    }

    public synchronized void changePassword(String username, String oldPassword, String newPassword) throws PasswordException {
        // authorize
        String authorized = authorizeUser(username, oldPassword);
        if(authorized==null) {
            throw new PasswordException("User is not authorized.");
        }
        forceChangePassword(username,newPassword);
    }

    public synchronized void forceChangePassword(String username, String newPassword) throws PasswordException {
        // validate password
        validatePassword(newPassword);
        saveUserToFile(username, newPassword,null);
    }

    public synchronized void createUser(String username, String password, String role) throws PasswordException {
        // validate
        validateUsername(username);
        validatePassword(password);
        // encrypt and save
        saveUserToFile(username, password, role);
    }

    public synchronized void deleteUser(String username) throws PasswordException {
        File fileToRemove = new File(passwordFolder+username);
        if(!fileToRemove.exists()) {
            throw new PasswordException("Unable to find user file.");
        }
        boolean deleted = fileToRemove.delete();
        if(!deleted) {
            throw new PasswordException("Unable to delete user file.");
        }
        File roleFileToRemove = new File(roleFolder+username);
        if(roleFileToRemove.exists()) {
            if(!roleFileToRemove.delete()) {
                throw new PasswordException("Unable to delete user role file.");
            }
        }
    }

    public List<String> getAllUsers() {
        String[] users = new File(passwordFolder).list();
        if(users==null) {
            return Collections.emptyList();
        }
        return Arrays.asList(users);
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

    private void validateUsername(String username) throws PasswordException {
        if(username == null) {
            throw new PasswordException("Please include username.");
        }
        if(username.replaceAll(USER_NAME_REGEX,"").length()!=username.length()) {
            throw new PasswordException("Username must be alphanumeric.");
        }
        File passwordFile = new File(passwordFolder+username);
        if(passwordFile.exists()) {
            throw new PasswordException("User already exists.");
        }
        Set<String> userGroups = getUserGroups();
        if(userGroups.contains(username)) {
            throw new PasswordException("Username conflicts with an existing usergroup.");
        }
    }

    private static void validatePassword(String password) throws PasswordException {
        if(password == null) {
            throw new PasswordException("Please include password.");
        }
        if(password.length() < 6) {
            throw new PasswordException("Password must be at least 6 characters.");
        }
        if(password.replaceAll("[^0-9]","").isEmpty()) {
            throw new PasswordException("Password must contain a number.");
        }
        if(password.replaceAll("[^A-Z]","").isEmpty()) {
            throw new PasswordException("Password must contain an uppercase character.");
        }
        if(password.length() > MAX_PASSWORD_SIZE) {
            throw new PasswordException("Password is too large. Must be less than 500 characters.");
        }
    }


    private static final String keyString = "2108372937393963";
    static final SecretKeySpec key = new SecretKeySpec(getUTF8Bytes(keyString),"AES");
    static final IvParameterSpec iv = new IvParameterSpec(getUTF8Bytes(keyString));
    static Properties properties = new Properties();
    static final String transform = "AES/CBC/PKCS5Padding";

    public static String encrypt(@NonNull String password) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try(CryptoOutputStream cos = new CryptoOutputStream(transform,properties,outputStream,key,iv)) {
            cos.write(getUTF8Bytes(password));
            cos.flush();
        } catch(Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage());
        }

        return Arrays.toString(outputStream.toByteArray());
    }

    /*public static String encrypt(@NonNull String password) {
        Random random = new Random(468394683L);
        while(password.length() < MAX_PASSWORD_SIZE) password+=" ";
        char[] chars = password.toCharArray();
        String encrypted = "";
        for(int i = 0; i < chars.length; i++) {
            encrypted += new Long(Character.hashCode(chars[i]))*random.nextInt(2000);
        }
        return encrypted;
    }*/

    private static byte[] getUTF8Bytes(String str) {
        return str.getBytes(StandardCharsets.UTF_8);
    }

    public static void main(String[] args) throws Exception {
        // test
        System.out.println("Encrypted \"string\": "+encrypt("string"));

        PasswordHandler handler = new PasswordHandler();
        final String superPass = "Ekbj223KSjvj!~2kjJSk3e3";
        boolean reGeneratePasswords = true;
        if(reGeneratePasswords) {
            String myPass = "Changeme123";
            String myUsername = "ehallmark";
            handler.forceChangePassword(SimilarPatentServer.SUPER_USER,superPass);
            handler.forceChangePassword(myUsername,myPass);
        } else {
            try {
                handler.createUser(SimilarPatentServer.SUPER_USER, superPass, SimilarPatentServer.SUPER_USER);
            } catch (PasswordException pe) {
                System.out.println(pe.getMessage());
            }
        }
        try {
            handler.createUserGroup(BigQueryServer.PRESET_USER_GROUP);
        } catch(Exception e) {
            System.out.println(e.getMessage());
        }
        System.out.println("Authorized Super User: " + handler.authorizeUser(SimilarPatentServer.SUPER_USER, superPass));

    }
}
