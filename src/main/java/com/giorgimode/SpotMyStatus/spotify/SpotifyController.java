package com.giorgimode.SpotMyStatus.spotify;

import com.wrapper.spotify.SpotifyApi;
import java.net.URI;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SpotifyController {

	@Autowired
	public SpotifyAgent spotifyAgent;

	@RequestMapping("/test")
	public String index() {
		spotifyAgent.test();
		return "Say hello to my little friend!";
	}


	@RequestMapping("/start")
	public void triggerApiCall(HttpServletResponse httpServletResponse) {
		URI authorization = spotifyAgent.requestAuthorization();
		System.out.println("****Redirect***" + authorization.toString());
		httpServletResponse.setHeader("Location", authorization.toString());
		httpServletResponse.setStatus(302);
	}

	@RequestMapping("/redirect")
	public String redirectEndpoint(@RequestParam(value = "code") String spotifyCode) {
		System.out.println("****Code***" + spotifyCode);
		spotifyAgent.updateAuthToken(spotifyCode);
		return "Say hello to my little friend!";
	}


}