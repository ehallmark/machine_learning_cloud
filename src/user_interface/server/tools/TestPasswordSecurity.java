package user_interface.server.tools;


import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;

public class TestPasswordSecurity {
    public static void main(String[] args) {
        final long total = 1000000000;
        char[] letters = new char[]{
                'a','b','c','d','e','f','h','i','j','k','l','m','n','o','p','q','r','s','t','u','v','w','x','y','z',
                'A','B','C','D','E','F','H','I','J','K','L','M','N','O','P','Q','R','S','T','U','V','W','X','Y','Z',
                '1','2','3','4','5','6','7','8','9','0','~','!','@','#','$','%','^','&','*','(',')','_','-','+','=',' ',
                '.',',','?','<','>',':',';','\'','"'
        };
        String password = "Evan12352!!";

        PasswordHandler passwordHandler = new PasswordHandler();

        String encrypted = passwordHandler.encrypt(password);

        Random rand = new Random(2352);

        AtomicLong cnt = new AtomicLong(0);
        AtomicLong failures = new AtomicLong(0);
        LongStream.range(0,total).parallel().forEach(i->{
            StringBuilder builder = new StringBuilder();
            final int c = rand.nextInt(10)+1;
            for(int j = 0; j < c; j++) {
                builder.append(letters[rand.nextInt(letters.length)]);
            }
            String attempt = builder.toString();
            if(attempt.equals(password)) return;

            //System.out.println("Attempt: "+attempt);

            String encAttempt = passwordHandler.encrypt(attempt);
            if(encAttempt.equals(encrypted)) {
                System.out.println("FAILURE");
                failures.getAndIncrement();
            }
            if(cnt.getAndIncrement()%10000==9999) {
                System.out.println("Failed: " + failures.get() + " out of " + cnt.get() + ".  Remaining: " + (total - cnt.get()));
            }
        });

        System.out.println("Num failures: "+failures.get()+" out of "+cnt.get());
    }
}
