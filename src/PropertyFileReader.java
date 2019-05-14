import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Properties;

import javax.crypto.IllegalBlockSizeException;
import javax.naming.AuthenticationException;
import javax.naming.CommunicationException;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;

import com.startinpoint.utils.DesEncrypter;

/**
 * @author SwanHtetKyaw
 *
 */

public class PropertyFileReader {

	public static void main(String[] args) {
		String propertyFileURL = "./application.properties";
		Properties refProp = new Properties();

		String refFileURL = "./reference.properties";
		FileReader fileReader;
		try {
			fileReader = new FileReader(refFileURL);
			refProp.load(fileReader);

			propertiesValidatityTest(refProp, propertyFileURL);
		} catch (FileNotFoundException e) {
			System.out.println("Reference Property file not found!!");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void propertiesValidatityTest(Properties refProp, String propertyFileURL) {
		Properties prop = new Properties();

		String[] urls = refProp.getProperty("urls").split(" \\| ");
		String[] userNames = refProp.getProperty("usernames").split(" \\| ");
		String[] passwords = refProp.getProperty("passwords").split(" \\| ");
		String dbDriver = refProp.getProperty("driver");

		FileReader propertyFileReader;
		try {
			propertyFileReader = new FileReader(propertyFileURL);
			prop.load(propertyFileReader);

			dbConnectionTest(prop, urls, userNames, passwords, dbDriver);
			restServicesTest(refProp, prop);
			ldapTest(prop);
		} catch (FileNotFoundException e1) {
			System.out.println("property file not found");
		} catch (IOException e) {
			System.out.println("io exception occured in loading property file");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void dbConnectionTest(Properties prop, String[] urls, String[] userNames, String[] passwords,
			String dbDriver) {
		for (int i = 0; i < urls.length; i++) {
			String dbURL = prop.getProperty(urls[i]);
			String dbUserName = prop.getProperty(userNames[i]);
			try {

				DesEncrypter de = new DesEncrypter();

				String dbPassword = de.decrypt(prop.getProperty(passwords[i]));


				Class.forName(dbDriver);
				Connection dbCon = DriverManager.getConnection(dbURL, dbUserName, dbPassword);

				DatabaseMetaData dbMetaData = dbCon.getMetaData();

				String dbName = dbMetaData.getDatabaseProductName();
				String dbVersion = dbMetaData.getDatabaseProductVersion();
				System.out.println(
						"DB(" + dbURL + ") connection success with " + dbName + "DB with version " + dbVersion);
			} catch (ClassNotFoundException e) {
				System.out.println("Driver class error occured for url: " + urls[i] + " with driver: " + dbDriver);
			} catch (SQLException e) {
				System.out.println("Connection failure for url: " + urls[i] + "=" + dbURL);
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				System.out.println();
			}
		}
	}

	private static void restServicesTest(Properties refProp, Properties prop) {
		if (prop != null) {
			String[] restUrls = refProp.getProperty("restUrls").split(" \\| ");
			String[] restAuthTypes = refProp.getProperty("auth.types").split(" \\| ");
			String[] restBaseUrls = refProp.getProperty("baseURLs").split(" \\| ");
			try {
				DesEncrypter de = new DesEncrypter();
				for (int i = 0; i < restUrls.length; i++) {
					String restURL = prop.getProperty(restUrls[i]);
					String restAuthType = prop.getProperty(restAuthTypes[i]);
					String restBaseUrl = prop.getProperty(restBaseUrls[i]);
					String userName = "", password = "";
					if (!restAuthType.equalsIgnoreCase("none")) {
						userName = prop.getProperty(refProp.getProperty("restUserName").split(" \\| ")[i]);
						password = de.decrypt(prop.getProperty(refProp.getProperty("restPassword").split(" \\| ")[i]));
					}
					if (restAuthType.equalsIgnoreCase("basic")) {
						try {
							URL url = new URL(restURL + restBaseUrl);
							String encodeAuth = Base64.getEncoder()
									.encodeToString((userName + ":" + password).getBytes("UTF-8"));

							HttpURLConnection connection = (HttpURLConnection) url.openConnection();
							connection.setRequestMethod("GET");
							connection.setRequestProperty("Authorization", "Basic " + encodeAuth);

							System.out.println("rest url: " + restURL
									+ (connection.getResponseCode() >= 200 && connection.getResponseCode() <= 299
											? " successfully connected" : " connection failed"));
						} catch (MalformedURLException e) {
							System.out.println("Rest url erorr occured");
						} catch (UnsupportedEncodingException e) {
							System.out.println("Rest auth encoding error occured");
						} finally {
							System.out.println();
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

		}
	}

	@SuppressWarnings({ "rawtypes", "unused" })
	private static void ldapTest(Properties prop) {

		try {
			DesEncrypter de = new DesEncrypter();
			String ldapUrl = prop.getProperty("ldap.url");
			String ldapBaseN = prop.getProperty("ldap.basedn");
			String ldapPrefix = prop.getProperty("ldap.prefix");
			String ldapUsername = prop.getProperty("ldap.username");
			String ldapPassword = de.decrypt(prop.getProperty("ldap.password"));

			Properties initialProperties = new Properties();
			initialProperties.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
			initialProperties.put(Context.PROVIDER_URL, ldapUrl);
			initialProperties.put(Context.SECURITY_PRINCIPAL, ldapPrefix + ldapUsername);
			initialProperties.put(Context.SECURITY_CREDENTIALS, ldapPassword);
			initialProperties.put(Context.REFERRAL, "follow");
			DirContext context = new InitialDirContext(initialProperties);
			String searchFilter = "objectclass=person";
			SearchControls controls = new SearchControls();
			controls.setSearchScope(SearchControls.SUBTREE_SCOPE);

			NamingEnumeration users = context.search("DC=dev,DC=startinpoint,DC=com", searchFilter, controls);

			System.out.println("Ldap connection successful");
		} catch (CommunicationException | ConnectException e) {
			System.out.println("Ldap url is incorrect");
		} catch (AuthenticationException e) {
			System.out.println("Ldap username or prefix incorrect");
		} catch (IllegalBlockSizeException e) {
			System.out.println("Ldap password incorrect");
		} catch (NamingException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
