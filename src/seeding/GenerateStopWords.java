package seeding;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GenerateStopWords {
	public static void main(String[] args) {
		try {
			Database.setupMainConn();
			ResultSet rs = Database.getStopWords(500);
			List<String> words = new ArrayList<String>();
			while(rs.next()) {
				if(!Constants.STOP_WORD_SET.contains(words)) words.add('"'+rs.getString(1)+'"');
			}
			String[] wordArray = words.toArray(new String[]{});
			System.out.println(Arrays.toString(wordArray));
			
		} catch(SQLException e) {
			e.printStackTrace();
		}
	}
}
