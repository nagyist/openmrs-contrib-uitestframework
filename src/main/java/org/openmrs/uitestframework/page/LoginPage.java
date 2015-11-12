package org.openmrs.uitestframework.page;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

public class LoginPage extends AbstractBasePage {
	
	static final By USERNAME = By.id("username");
	static final By PASSWORD = By.id("password");
	static final By LOGIN = By.id("loginButton");
	static final By LOCATIONS = By.cssSelector("#sessionLocation li");
	public static final String LOGIN_PATH = "/login.htm";
	static final String LOGOUT_PATH = "/logout";
	static final String CLERK_USERNAME = "clerk";
	static final String CLERK_PASSWORD = "Clerk123";
	static final String NURSE_USERNAME = "nurse";
	static final String NURSE_PASSWORD = "Nurse123";
	static final String DOCTOR_USERNAME = "doctor";
	static final String DOCTOR_PASSWORD = "Doctor123";
	static final String SYSADMIN_USERNAME = "sysadmin";
	static final String SYSADMIN_PASSWORD = "Sysadmin123";

	private String UserName;
	
	private String Password;
	
	public LoginPage(WebDriver driver) {
		super(driver);
		UserName = properties.getUserName();
		Password = properties.getPassword();
	}
	
	public void login(String user, String password, int location) {
		waitForPageToBeReady(true);

		postLoginForm(user, password, location);
		
		findElement(byFromHref(URL_ROOT + LOGOUT_PATH)); // this waits until the log off link is present
	}

	private void postLoginForm(String user, String password, int location) {
	    String postJs;
		InputStream in = null;
		try {
			in = getClass().getResourceAsStream("/post.js");
	        postJs = IOUtils.toString(in);
	        in.close();
        } catch (IOException e) {
	        throw new RuntimeException(e);
        } finally {
        	IOUtils.closeQuietly(in);
        }
		
		((JavascriptExecutor) driver).executeScript(postJs + " post('" + expectedUrlPath() +"', {username: '" + user + "', password: '" + password + "', sessionLocation: " + location + "});");
    }
	
	public void login(String user, String password) {
		login(user, password, 1);
	}
	
	public void loginAsAdmin() {
		login(UserName, Password);
	}
	
	@Override
	public String expectedUrlPath() {
		return URL_ROOT + LOGIN_PATH;
	}

	public void loginAsClerk() {
	    login(CLERK_USERNAME, CLERK_PASSWORD);
    }

	public void loginAsNurse() {
		login(NURSE_USERNAME, NURSE_PASSWORD);
    }

	public void loginAsDoctor() {
		login(DOCTOR_USERNAME, DOCTOR_PASSWORD);
    }

	public void loginAsSysadmin() {
		login(SYSADMIN_USERNAME, SYSADMIN_PASSWORD);
    }
}
