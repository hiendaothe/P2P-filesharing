package util;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;


public class Util {
	
	public static ArrayList<String> listFilesForFolder(final File folder) {
		
		ArrayList<String> fileNames = new ArrayList<String>();
	    
		for (final File fileEntry : folder.listFiles()) 
	    	fileNames.add(fileEntry.getName());
		
		return fileNames;

	}
	
	public static String copy(InputStream in, OutputStream out) throws IOException {
		String message = "";
		byte[] buffer = new byte[1024];
		int count = 0;
		long start = System.currentTimeMillis();
		try {
			while ((count = in.read(buffer)) != -1) {
				out.write(buffer, 0, count);
			}
		} catch (IOException e) {
			message = "Can't continue download file, Peer is disconnected";
			throw e; // rethrow the exception to be handled by the caller
		}
		// System.out.println("Download took " + (System.currentTimeMillis() - start) + " ms.");
		message = "Download took " + (System.currentTimeMillis() - start) + " ms.";
		return message;
	}
	
	public static long toSeconds(long start, long end){
		return (end-start)/1000;
	}
	
		public static String getExternalIP() throws IOException{
			URL url = new URL("http", "checkip.amazonaws.com", -1, "/");
	    	BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
	    	return br.readLine();
		}
	
	public static double calculateAverage(ArrayList<Long> times) {
		  Long sum = 0L;
		  if(!times.isEmpty()) {
		    for (Long time : times) {
		        sum += time;
		    }
		    return sum.doubleValue() / times.size();
		  }
		  return sum;
		}
}
