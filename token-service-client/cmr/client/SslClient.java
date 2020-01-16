package cmr.client;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.List;

public class SslClient {

	private static final String LINE_BREAKER = System.getProperty("line.separator");
	// Launchpad Token Service root url, this is set to the Sandbox
	private static final String LAUNCHPAD_TOKEN_SERVICE_URL_ROOT = "https://api.launchpad-sbx.nasa.gov/";
	// full path of cacerts which has the PKI certificate for connecting to token service
	private static final String CACERTS_PATH = "/Users/yliu10/cacerts";
	// keystore password
	private static final String KEYSTORE_PASSWD = "xxxxxx";

	private static SSLSocketFactory getFactory( File pKeyFile, String pKeyPassword ) throws Exception {
		KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
		KeyStore keyStore = KeyStore.getInstance("JKS");

		InputStream keyInput = new FileInputStream(pKeyFile);
		keyStore.load(keyInput, pKeyPassword.toCharArray());
		keyInput.close();

		keyManagerFactory.init(keyStore, pKeyPassword.toCharArray());

		SSLContext context = SSLContext.getInstance("TLS");

		context.init(keyManagerFactory.getKeyManagers(), null, new SecureRandom());

		return context.getSocketFactory();
	}

	public static String getToken() {
		BufferedReader bufferedReader = null;
		InputStream is = null;
		try {
			URL url = new URL(LAUNCHPAD_TOKEN_SERVICE_URL_ROOT + "icam/api/sm/v1/gettoken");

			HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
				SSLSocketFactory factory = getFactory(new File(CACERTS_PATH), KEYSTORE_PASSWD);
			con.setSSLSocketFactory(factory);
			con.setConnectTimeout(10000);

			//Process response
			is = con.getInputStream();

			bufferedReader = new BufferedReader(new InputStreamReader(is));
			String line;
			StringBuffer lines = new StringBuffer();
			while ((line = bufferedReader.readLine()) != null) {
				lines.append(line).append(LINE_BREAKER);
			}

			JSONObject json = (JSONObject) JSONValue.parse(lines.toString());
			return (String) json.get("sm_token");
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return null;
		}

	}

	public static void validateToken(String token) {
		JSONObject tokenJson   = new JSONObject();
		tokenJson.put("token", token);

		try {
			URL url = new URL(LAUNCHPAD_TOKEN_SERVICE_URL_ROOT + "icam/api/sm/v1/validate");

			HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
				SSLSocketFactory factory = getFactory(new File(CACERTS_PATH), KEYSTORE_PASSWD);
			con.setSSLSocketFactory(factory);

			con.setRequestMethod("POST");
			con.setDoOutput(true);
			con.setRequestProperty("Content-Type","application/json; charset=UTF-8");
			DataOutputStream dos = new DataOutputStream(con.getOutputStream());
			dos.write(tokenJson.toString().getBytes("UTF-8"));

			dos.flush();
			dos.close();
			con.setConnectTimeout(10000);
			con.connect();

			BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()));
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = br.readLine()) != null) {
				sb.append(line+"\n");
			}
			br.close();

			JSONObject json = (JSONObject) JSONValue.parse(sb.toString());
			System.out.println("owner_auid: " + json.get("owner_auid"));
			List<String> groups = (List<String>) json.get("owner_groups");
			for (String group : groups) {
				if (group.contains("GSFC-CMR_INGEST_SIT")) {
					System.out.println("matched group: " + group);
				}
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		String op = "get-token";
		if (args.length >= 1) {
			op = args[0];
		}

		if (op.equals("get-token")) {
			String token= getToken();
			System.out.println("Token: " + token);
		}
		else if (op.equals("validate-token")) {
			// System.out.println("validate token: " + args[1]);
			validateToken(args[1]);
		}
		else {
			System.out.println("Usage: java SslClient get-token or validate-token <token>");
		}
	}
}
