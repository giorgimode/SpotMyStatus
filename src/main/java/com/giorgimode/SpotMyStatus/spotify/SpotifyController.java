package com.giorgimode.SpotMyStatus.spotify;

import com.giorgimode.SpotMyStatus.slack.SlackAgent;
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

	@Autowired
	public SlackAgent slackAgent;

	@RequestMapping("/start")
	public void triggerApiCall(HttpServletResponse httpServletResponse) {
		URI authorization = spotifyAgent.requestAuthorization();
		System.out.println("****Redirect***" + authorization.toString());
		httpServletResponse.setHeader("Location", authorization.toString());
		httpServletResponse.setStatus(302);
	}

	@RequestMapping("/redirect")
	public void redirectEndpoint(@RequestParam(value = "code") String spotifyCode, HttpServletResponse httpServletResponse) {
		System.out.println("****Code***" + spotifyCode);
		spotifyAgent.updateAuthToken(spotifyCode);
		String authorization = slackAgent.requestAuthorization();
		System.out.println("****Redirect***" + authorization);
		httpServletResponse.setHeader("Location", authorization);
		httpServletResponse.setStatus(302);
	}


}