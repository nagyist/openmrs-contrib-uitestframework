package org.openmrs.uitestframework.test;

import static org.dbunit.database.DatabaseConfig.PROPERTY_DATATYPE_FACTORY;
import static org.dbunit.database.DatabaseConfig.PROPERTY_METADATA_HANDLER;
import static org.junit.Assert.assertEquals;
import static org.openmrs.uitestframework.test.TestData.checkIfPatientExists;

import java.io.StringReader;
import java.net.URL;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.ws.rs.NotFoundException;

import com.saucelabs.junit.ConcurrentParameterized;
import com.saucelabs.junit.Parallelized;
import org.apache.commons.lang3.StringUtils;
import org.dbunit.IDatabaseTester;
import org.dbunit.JdbcDatabaseTester;
import org.dbunit.database.AmbiguousTableNameException;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.QueryDataSet;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.xml.FlatXmlDataSet;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.ext.mysql.MySqlDataTypeFactory;
import org.dbunit.ext.mysql.MySqlMetadataHandler;
import org.dbunit.operation.DatabaseOperation;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.openmrs.uitestframework.page.GenericPage;
import org.openmrs.uitestframework.page.LoginPage;
import org.openmrs.uitestframework.page.Page;
import org.openmrs.uitestframework.page.TestProperties;
import org.openmrs.uitestframework.test.TestData.EncounterInfo;
import org.openmrs.uitestframework.test.TestData.PatientInfo;
import org.openmrs.uitestframework.test.TestData.RoleInfo;
import org.openmrs.uitestframework.test.TestData.TestPatient;
import org.openmrs.uitestframework.test.TestData.TestProvider;
import org.openmrs.uitestframework.test.TestData.UserInfo;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;

import com.saucelabs.common.SauceOnDemandAuthentication;
import com.saucelabs.common.SauceOnDemandSessionIdProvider;
import com.saucelabs.junit.SauceOnDemandTestWatcher;

/**
 * Superclass for all UI Tests. Contains lots of handy "utilities"
 * needed to setup and tear down tests as well as handy methods
 * needed during tests, such as:
 *  - initialize Selenium WebDriver
 *  - create (and delete) test patient, @see {@link #createTestPatient()}
 *  - @see {@link #currentPage()}
 *  - @see {@link #assertPage(Page)}
 *  - @see {@link #pageContent()}
 */
@RunWith(ConcurrentParameterized.class)
public class TestBase implements SauceOnDemandSessionIdProvider {

	/**
	 * Constructs a {@link SauceOnDemandAuthentication} instance using the supplied user name/access key.  To use the authentication
	 * supplied by environment variables or from an external file, use the no-arg {@link SauceOnDemandAuthentication} constructor.
	 */
	public SauceOnDemandAuthentication authentication = new SauceOnDemandAuthentication("tmsaucelabs", "3547e04d-e4f7-4bf1-9e67-1366887f29ab");

	/**
	 * JUnit Rule which will mark the Sauce Job as passed/failed when the test succeeds or fails.
	 */
	@Rule
	public SauceOnDemandTestWatcher resultReportingTestWatcher = new SauceOnDemandTestWatcher(this, authentication);

	/**
	 * Represents the browser to be used as part of the test run.
	 */
	private String browser = "chrome";
	/**
	 * Represents the operating system to be used as part of the test run.
	 */
	private String os = "Windows 8.1";
	/**
	 * Represents the version of the browser to be used as part of the test run.
	 */
	private String version = "43.0";
	/**
	 * Instance variable which contains the Sauce Job Id.
	 */
	private String sessionId;

	/**
	 * The {@link WebDriver} instance which is used to perform browser interactions with.
	 */
	protected WebDriver driver;

	protected static IDatabaseTester dbTester;

	protected static QueryDataSet deleteDataSet;

	protected static QueryDataSet checkDataSet;

	public static final String DEFAULT_ROLE = "Privilege Level: Full";

	protected LoginPage loginPage;

	/**
	 * @return a LinkedList containing String arrays representing the browser combinations the test should be run against. The values
	 * in the String array are used as part of the invocation of the test constructor
	 */
	@ConcurrentParameterized.Parameters
	public static LinkedList browsersStrings() {
		LinkedList browsers = new LinkedList();

		browsers.add(new String[]{"Windows 8.1", "43.0", "chrome", null, null});
		return browsers;
	}


	@Before
	public void startWebDriver() throws Exception {
		DesiredCapabilities capabilities = new DesiredCapabilities();
		capabilities.setCapability(CapabilityType.BROWSER_NAME, browser);
		if (version != null) {
			capabilities.setCapability(CapabilityType.VERSION, version);
		}
		capabilities.setCapability(CapabilityType.PLATFORM, os);
		capabilities.setCapability("name", "Sauce Sample Test");
		this.driver = new RemoteWebDriver(
				new URL("http://" + authentication.getUsername() + ":" + authentication.getAccessKey() + "@ondemand.saucelabs.com:80/wd/hub"),
				capabilities);
		driver.manage().timeouts().pageLoadTimeout(270, TimeUnit.SECONDS);
		driver.manage().timeouts().setScriptTimeout(270, TimeUnit.SECONDS);
		this.sessionId = (((RemoteWebDriver) driver).getSessionId()).toString();

		loginPage = new LoginPage(driver);

		goToLoginPage();
	}

	@After
	public void stopWebDriver() {
		driver.quit();
	}

	public void login() {
		assertPage(loginPage);
		loginPage.loginAsAdmin();
	}

	public static IDatabaseTester getDbTester() throws Exception {
		if (dbTester == null) {
			initDatabaseConnection();
		}
		return dbTester;
	}

	private static void initDatabaseConnection() throws Exception {
		final TestProperties properties = TestProperties.instance();
		dbTester = new JdbcDatabaseTester(properties.getDatabaseDriverclass(), properties.getDatabaseConnectionUrl(),
				properties.getDatabaseUsername(), properties.getDatabasePassword(), properties.getDatabaseSchema()) {
			// A bit of an ugly hack here, due to the fact that DbUnit is really intended for junit3
			// but we're using it in junit4. When you use it with junit3, the getConnection method
			// takes care of the config.setProperty calls for you. (see org.dbunit.DBTestCase.getConnection()
			// and org.dbunit.ext.mysql.MySqlConnection.MySqlConnection(Connection, String).
			@Override
			public IDatabaseConnection getConnection() throws Exception {
				IDatabaseConnection conn = super.getConnection();
				DatabaseConfig config = conn.getConfig();
				config.setProperty(PROPERTY_DATATYPE_FACTORY, new MySqlDataTypeFactory());
				config.setProperty(PROPERTY_METADATA_HANDLER, new MySqlMetadataHandler());
				return conn;
			}
		};
	}

	/**
	 * Typically invoked from an @Before method.
	 */
	public void dbUnitSetup() throws Exception {
		getDbTester().setDataSet(dbUnitDataset());
		getDbTester().setSetUpOperation(dbUnitSetUpOperation());
		getDbTester().onSetup();
	}

	/**
	 * Override to setup a pre-test dataset.
	 */
	protected IDataSet dbUnitDataset() throws DataSetException {
		// empty dataset
		String inputXml = "<dataset></dataset>";
		IDataSet dataset = new FlatXmlDataSetBuilder().build(new StringReader(inputXml));
		return dataset;
	}

	/**
	 * Override to change how DbUnit operates.
	 */
	protected DatabaseOperation dbUnitSetUpOperation() {
		return DatabaseOperation.REFRESH;
	}

	/**
	 * Typically invoked from an @After method.
	 */


	public void dbUnitTearDown() throws Exception {
		dbUnitTearDownStatic(dbUnitTearDownOperation());
	}

	/**
	 * Typically invoked from an @AfterClass method.
	 */
	public static void dbUnitTearDownStatic() throws Exception {
		dbUnitTearDownStatic(DatabaseOperation.DELETE);
	}

	public static void dbUnitTearDownStatic(DatabaseOperation op) throws Exception {
		if (deleteDataSet == null) {
			return;
		}
		getDbTester().setDataSet(deleteDataSet);
//System.out.println("teardown dataset: " + Arrays.asList(getDbTester().getDataSet().getTableNames()));
		getDbTester().setTearDownOperation(op);
		getDbTester().onTearDown();
		deleteDataSet = null;
	}

	/**
	 * Override to change how DbUnit operates.
	 */
	protected DatabaseOperation dbUnitTearDownOperation() {
		return DatabaseOperation.DELETE;
	}

	protected static QueryDataSet getDeleteDataSet() throws Exception {
		if (deleteDataSet == null) {
			deleteDataSet = newQueryDataSet();
		}
		return deleteDataSet;
	}

	protected static QueryDataSet getCheckDataSet() throws Exception {
		if (checkDataSet == null) {
			checkDataSet = newQueryDataSet();
		}
		return checkDataSet;
	}

	private static QueryDataSet newQueryDataSet() throws Exception {
		return new QueryDataSet(getDbTester().getConnection());
	}

	public void goToLoginPage() {
		currentPage().gotoPage(LoginPage.LOGIN_PATH);
	}

	/**
	 * Return a Page that represents the current page, so that all the convenient methods in Page
	 * can be used.
	 *
	 * @return a Page
	 */
	public Page currentPage() {
		return new GenericPage(driver);
	}

	/**
	 * Assert we're on the expected page. 
	 *
	 * @param expected page
	 */
	public void assertPage(Page expected) {
		assertEquals(expected.expectedUrlPath(), currentPage().urlPath());
	}

	public String patientIdFromUrl() {
		String url = driver.getCurrentUrl();
		return StringUtils.substringAfter(url, "patientId=");
	}

	/**
	 * Delete the given patient from the various tables that contain
	 * portions of a patient's info.
	 *
	 * @param uuid The uuid of the patient to delete.
	 */
	public void deletePatient(String uuid) throws NotFoundException {
		RestClient.delete("patient/" + uuid);
	}



	/**
	 * Delete the given user from the various tables that contain
	 * portions of a user's info. 
	 *
	 * @param user The database user info, especially the user_id and person_id.
	 */
	public static void deleteUser(UserInfo user) throws Exception {
		// See org.openmrs.module.mirebalais.smoke.helper.UserDatabaseHandler.addUserForDelete(String) for more details.
		String userid = user.userId;
		String personid = user.id;
		QueryDataSet dataSet = getDeleteDataSet();
		addSimpleQuery(dataSet, "person", "person_id", personid);
		addSimpleQuery(dataSet, "provider", "person_id", personid);
		addSimpleQuery(dataSet, "person_name", "person_id", personid);
		addSimpleQuery(dataSet, "person_address", "person_id", personid);
		dataSet.addTable("name_phonetics", formatQuery("select * from name_phonetics where person_name_id in (select person_name_id from person_name where person_id = %s)", personid));
		addSimpleQuery(dataSet, "person_attribute", "person_id", personid);
		addSimpleQuery(dataSet, "users", "user_id", userid);
		addSimpleQuery(dataSet, "user_role", "user_id", userid);
		addSimpleQuery(dataSet, "user_property", "user_id", userid);
		dbUnitTearDownStatic();
	}

	/**
	 * Delete the given role from the role table, if it was created
	 * by this framework.  
	 *
	 * @param user The database role info.
	 */
	public static void deleteRole(RoleInfo role) throws Exception {
		if (! role.created) {
			return;
		}
		QueryDataSet dataSet = getDeleteDataSet();
		addSimpleQuery(dataSet, "role", "uuid", '"' + role.uuid + '"');
		dbUnitTearDownStatic();
	}

	static void addSimpleQuery(QueryDataSet dataSet, String tableName, String columnName, String id) throws AmbiguousTableNameException {
		String query = formatQuery(simpleQuery(tableName, columnName), id);
//System.out.println("addSimpleQuery: " + tableName + " " + query);		
		dataSet.addTable(tableName, query);
	}

	static String simpleQuery(String tableName, String columnName) {
		return "select * from " + tableName + " where " + columnName + " = %s";
	}

	static String formatQuery(String query, String id) {
		return String.format(query, id);
	}

	public PatientInfo createTestPatient(String patientIdentifierTypeName, String source) {
		PatientInfo pi = TestData.generateRandomPatient();
		String uuid = TestData.createPerson(pi);
		pi.identifier = createPatient(uuid, patientIdentifierTypeName, source);
		return pi;
	}

	public PatientInfo createTestPatient() {
		return createTestPatient(TestData.OPENMRS_PATIENT_IDENTIFIER_TYPE, "1");
	}

	/**
	 * Create a Patient in the database and return its Patient Identifier.
	 * The Patient Identifier is obtained from the database.
	 *
	 * @param personUuid The person 
	 * @param patientIdentifierType The type of Patient Identifier to use
	 * @return The Patient Identifier for the newly created patient
	 */
	public String createPatient(String personUuid, String patientIdentifierType, String source) {
		String patientIdentifier = generatePatientIdentifier(source);
		RestClient.post("patient", new TestPatient(personUuid, patientIdentifier, patientIdentifierType));
		return patientIdentifier;
	}

	private String generatePatientIdentifier(String source) {
		return RestClient.generatePatientIdentifier(source);
	}

	/**
	 * Returns the entire text of the "content" part of the current page
	 *
	 * @return the entire text of the "content" part of the current page
	 */
	public String pageContent() {
		return driver.findElement(By.id("content")).getText();
	}

	public EncounterInfo createTestEncounter(String encounterType, PatientInfo patient) {
		EncounterInfo ei = new EncounterInfo();
		ei.datetime = "2012-01-04";	// arbitrary
		ei.type = TestData.getEncounterTypeUuid(encounterType);
		ei.patient = patient;
		TestData.createEncounter(ei);	// sets the uuid
		return ei;
	}

	/**
	 * Create a User in the database with the given Role and return its info.
	 */
	public static UserInfo createUser(String username, RoleInfo role) {
		UserInfo ui = (UserInfo) TestData.generateRandomPerson(new UserInfo());
		TestData.createPerson(ui);
		ui.username = username;
		ui.addRole(role);
		ui.addRole(DEFAULT_ROLE);
		TestData.createUser(ui);
		return ui;
	}

	/**
	 * Create a User and Provider in the database with the given role and provider-role and return its info.
	 */
	public static UserInfo createUser(String username, RoleInfo role, String providerRole) {
		return createUser(username, role, providerRole, null);
	}

	/**
	 * Create a User and Provider in the database with the given role and provider-role and return its info.
	 */
	public static UserInfo createUser(String username, RoleInfo role, String providerRole, String locale) {
		UserInfo ui = (UserInfo) TestData.generateRandomPerson(new UserInfo());
		ui.locale = locale;
		TestData.createPerson(ui);
		ui.username = username;
		ui.addRole(role);
		ui.addRole(DEFAULT_ROLE);
		TestData.createUser(ui);
		// create provider
		String providerUuid = (new TestProvider(ui.uuid, ui.givenName)).create();
		try {
			// Hack/workaround the fact that we cannot use REST to set the provider_role_id in the provider table.
			// This is the only place where we use DbUnit/JDBC directly during test setup, everywhere
			// else we use REST.
			String providerId = TestData.getId("provider", providerUuid);
			String xmlds = "<dataset>"
					+ "<provider "
					+ "provider_id='" + providerId
					+ "' uuid='" + providerUuid
					+ "' provider_role_id='" + getProviderRoleId(providerRole) + "'/>"
					+ "</dataset>";
			FlatXmlDataSet ds = new FlatXmlDataSetBuilder().build(new StringReader(xmlds));
			getDbTester().setDataSet(ds);
			getDbTester().setSetUpOperation(DatabaseOperation.UPDATE);
			getDbTester().onSetup();
		} catch (DataSetException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return ui;
	}

	// Part of the above hack to workaround lack of REST support for provider role.
	private static Integer getProviderRoleId(String providerRoleName) throws Exception {
		QueryDataSet ds = newQueryDataSet();
		ds.addTable("providermanagement_provider_role", "select * from providermanagement_provider_role where name = '" + providerRoleName + "'");
		ITable providerRole = ds.getTable("providermanagement_provider_role");
		return  (Integer) providerRole.getValue(0, "provider_role_id");
	}

	public static RoleInfo findOrCreateRole(String name) {
		RoleInfo ri = new RoleInfo(name);
		String uuid = TestData.getRoleUuid(name);
		if (uuid == null) {
			TestData.createRole(ri);
			ri.created = true;
		} else {
			ri.uuid = uuid;
			ri.created = false;
		}
		return ri;
	}

	public void login(UserInfo user) {
		LoginPage page = new LoginPage(driver);
		assertPage(page);
		page.login(user.username, user.password);
	}

	protected void waitForPatientDeletion(String uuid) throws Exception {
		Long startTime = System.currentTimeMillis();
		while(checkIfPatientExists(uuid)) {
			Thread.sleep(200);
			if(System.currentTimeMillis() - startTime > 30000) {
				throw new TimeoutException("Patient not deleted in expected time");
			}
		}
	}

	@Override
	public String getSessionId() {
		return sessionId;
	}

}