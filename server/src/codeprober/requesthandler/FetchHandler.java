package codeprober.requesthandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import codeprober.protocol.data.FetchReq;
import codeprober.protocol.data.FetchRes;

public class FetchHandler {

	public static FetchRes apply(FetchReq req) {
		// Client needs to bypass cors
		try {
			final URL url = new URL(req.url);
			final HttpURLConnection con = (HttpURLConnection) url.openConnection();
			con.setRequestMethod("GET");
			con.setConnectTimeout(5000);
			con.setReadTimeout(5000);

			int status = con.getResponseCode();
			if (status != 200) {
				throw new RuntimeException("Unexpected status code " + status);
			}
			BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
			String inputLine;
			final StringBuffer content = new StringBuffer();
			while ((inputLine = in.readLine()) != null) {
				content.append(inputLine + "\n");
			}
			con.disconnect();

			return new FetchRes(content.toString());
		} catch (IOException e) {
			System.out.println("Error when performing fetch request " + req.toJSON());
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
}
