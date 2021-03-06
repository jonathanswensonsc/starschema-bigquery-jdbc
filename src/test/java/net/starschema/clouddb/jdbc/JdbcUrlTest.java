package net.starschema.clouddb.jdbc;

import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;

import junit.framework.Assert;
import net.starschema.clouddb.jdbc.Oauth2Bigquery;
import net.starschema.clouddb.jdbc.BQConnection;
import net.starschema.clouddb.jdbc.BQStatement;
import net.starschema.clouddb.jdbc.BQSupportFuncts;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Created by steven on 10/21/15.
 */
public class JdbcUrlTest {

    private BQConnection bq;
    private String URL;
    private Properties properties;

    @Before
    public void setup() throws SQLException, IOException {
        properties = getProperties("/installedaccount.properties");
        URL = getUrl("/installedaccount.properties", null) + "&useLegacySql=true";;
        this.bq = new BQConnection(URL, new Properties());
    }

    @Test
    public void urlWithDefaultDatasetShouldWork() throws SQLException {
        Assert.assertEquals(properties.getProperty("dataset"), bq.getDataSet());
    }

    @Test
    public void projectWithColons() throws SQLException {
        String urlWithColonContainingProject = URL.replace(bq.getProjectId(), "example.com:project");
        try {
            BQConnection bqWithColons = new BQConnection(urlWithColonContainingProject, new Properties());
            Assert.assertEquals("example.com:project", bqWithColons.getProjectId());
            Assert.assertEquals("example.com:project", bqWithColons.getCatalog());
        } catch (SQLException e){
            throw new AssertionError(e);
        }
    }

    @Test
    public void mungedProjectName() throws SQLException {
        String urlWithUnderscoreContainingProject = URL.replace(bq.getProjectId(), "example_com__project");
        try {
            BQConnection bqWithUnderscores = new BQConnection(urlWithUnderscoreContainingProject, new Properties());
            Assert.assertEquals("example.com:project", bqWithUnderscores.getProjectId());
            Assert.assertEquals("example.com:project", bqWithUnderscores.getCatalog());
        } catch (SQLException e){
            throw new AssertionError(e);
        }
    }

    @Test
    public void urlWithTimeouts() throws SQLException {
        try {
            String url1 = URL + "&readTimeout=foo";
            new BQConnection(url1, new Properties());
        } catch (SQLException e){
            Assert.assertEquals("could not parse readTimeout parameter.", e.getMessage());
        }

        try {
            String url1 = URL + "&connectTimeout=foo";
            new BQConnection(url1, new Properties());
        } catch (SQLException e){
            Assert.assertEquals("could not parse connectTimeout parameter.", e.getMessage());
        }

        try {
            String url1 = URL + "&readTimeout=-1000";
            new BQConnection(url1, new Properties());
        } catch (SQLException e){
            Assert.assertEquals("readTimeout must be positive.", e.getMessage());
        }

        try {
            String url1 = URL + "&connectTimeout=-1000";
            new BQConnection(url1, new Properties());
        } catch (SQLException e){
            Assert.assertEquals("connectTimeout must be positive.", e.getMessage());
        }
    }

    @Test
    public void canRunQueryWithDefaultDataset() throws SQLException {
        BQStatement stmt = new BQStatement(properties.getProperty("projectid"), bq);

        // This should not blow up with a "No dataset specified" exception
        stmt.executeQuery("SELECT * FROM orders limit 1");
    }

    @Test
    public void canConnectWithPasswordProtectedP12File() throws SQLException, IOException {
        String url = getUrl("/protectedaccount.properties", null);
        BQConnection bqConn = new BQConnection(url, new Properties());

        BQStatement stmt = new BQStatement(properties.getProperty("projectid"), bqConn);
        stmt.executeQuery("SELECT * FROM orders limit 1");
    }

    @Test
    public void canConnectWithJSONFile() throws SQLException, IOException {
        String url = getUrl("/protectedaccount-json.properties", null);
        properties = getProperties("/protectedaccount-json.properties");
        properties.setProperty("path", "src/test/resources/bigquery_credentials_protected.json");
        BQConnection bqConn = new BQConnection(url, properties);

        BQStatement stmt = new BQStatement(properties.getProperty("projectid"), bqConn);
        stmt.executeQuery("SELECT * FROM orders limit 1");
    }

    @Test
    public void canConnectWithJsonAuthFileContentsInProperties() throws SQLException, IOException {
        String url = getUrl("/protectedaccount-json.properties", null);
        properties = getProperties("/protectedaccount-json.properties");
        String jsonContents = new String(Files.readAllBytes(Paths.get("src/test/resources/bigquery_credentials_protected.json")));
        Properties props = new Properties();
        props.setProperty("jsonAuthContents", jsonContents);
        BQConnection bqConn = new BQConnection(url, props);

        BQStatement stmt = new BQStatement(properties.getProperty("projectid"), bqConn);
        stmt.executeQuery("SELECT * FROM orders limit 1");
    }

    @Test
    public void canConnectWithOAuthAccessToken() throws SQLException, IOException, GeneralSecurityException {
        // generate access token from service account credentials
        Properties serviceProps = getProperties("/protectedaccount.properties");
        String accessToken = Oauth2Bigquery.generateAccessToken(
            serviceProps.getProperty("user"),
            serviceProps.getProperty("path"),
            serviceProps.getProperty("password"),
            null
        );

        Properties oauthProps = getProperties("/oauthaccount.properties");
        oauthProps.setProperty("oauthaccesstoken", accessToken);
        String url = BQSupportFuncts.constructUrlFromPropertiesFile(oauthProps, true, null);
        BQConnection bqConn = new BQConnection(url, new Properties());

        BQStatement stmt = new BQStatement(oauthProps.getProperty("projectid"), bqConn);
        stmt.executeQuery("SELECT * FROM orders limit 1");
    }

    @Test
    public void gettingUrlComponentsWorks() throws IOException {
        String url = getUrl("/protectedaccount.properties", null);
        Properties protectedProperties = getProperties("/protectedaccount.properties");
        Properties components = BQSupportFuncts.getUrlQueryComponents(url, new Properties());

        Assert.assertEquals(protectedProperties.getProperty("user"), components.getProperty("user"));
        Assert.assertEquals(protectedProperties.getProperty("password"), components.getProperty("password"));
        Assert.assertEquals(protectedProperties.getProperty("path"), components.getProperty("path"));
    }

    @Test
    public void connectionUseLegacySqlValueFromProperties() throws IOException, SQLException {
        String url = getUrl("/protectedaccount.properties", null);
        BQConnection bqConn = new BQConnection(url, new Properties());
        // default true
        Assert.assertEquals(bqConn.getUseLegacySql(), false);

        String newUrl = url + "&useLegacySql=false";
        BQConnection bqConn2 = new BQConnection(newUrl, new Properties());
        Assert.assertEquals(bqConn2.getUseLegacySql(), false);
    }

    @Test
    public void connectionMaxBillingBytesFromProperties() throws IOException, SQLException {
        String url = getUrl("/protectedaccount.properties", null);
        BQConnection bqConn = new BQConnection(url, new Properties());
        // default null
        Assert.assertNull(bqConn.getMaxBillingBytes());

        String newUrl = url + "&maxbillingbytes=1000000000";
        BQConnection bqConn2 = new BQConnection(newUrl, new Properties());
        Assert.assertEquals((long) bqConn2.getMaxBillingBytes(), 1000000000);
    }

    @Test
    public void maxBillingBytesOverrideWorks() throws IOException, SQLException {
        String url = getUrl("/protectedaccount.properties", null) + "&maxbillingbytes=1" + "&useLegacySql=true";
        BQConnection bqConn = new BQConnection(url, new Properties());
        BQStatement stmt = new BQStatement(properties.getProperty("projectid"), bqConn);

        // need to include NOW() otherwise this query will pull from cache & mess up the following ASSERTs
        String sqlStmt = "SELECT word, NOW() from publicdata:samples.shakespeare LIMIT 100";

        boolean didFailAsExpected = false;

        // limited-bytes query should fail
        try {
            stmt.executeQuery(sqlStmt, false);
        } catch (SQLException e) {
            Assert.assertTrue("Expected query to fail because it exceeds maximum billing bytes.", e.toString().contains("Query exceeded limit for bytes billed: 1."));
            didFailAsExpected = true;
        }

        Assert.assertTrue("Query did not fail as expected.", didFailAsExpected);

        // unlimited-bytes query should succeed
        stmt.executeQuery(sqlStmt, true);
    }

    @Test
    public void rootUrlOverrideWorks() throws IOException, SQLException {
        properties = getProperties("/vpcaccount.properties");
        // Add URL-encoded `https://restricted.googleapis.com/` to the JDBC URL.
        URL = getUrl("/vpcaccount.properties", null)
            + "&rootUrl=https%3A%2F%2Frestricted.googleapis.com%2F";
        // Mock a response similar to
        // https://cloud.google.com/bigquery/docs/reference/rest/v2/jobs/query#response-body
        String mockResponse =
            "{ \"jobComplete\": true, "
            + "\"totalRows\": \"0\", "
            + "\"rows\": [] }";
        MockHttpTransport mockTransport =
            new MockHttpTransport.Builder()
                .setLowLevelHttpResponse(
                    new MockLowLevelHttpResponse().setContent(mockResponse))
                .build();
        bq = new BQConnection(URL, properties, mockTransport);
        BQStatement stmt = new BQStatement(properties.getProperty("projectid"), bq);
        String sqlStmt = "SELECT word from publicdata:samples.shakespeare LIMIT 100";

        stmt.executeQuery(sqlStmt);

        MockLowLevelHttpRequest request = mockTransport.getLowLevelHttpRequest();
        Assert.assertTrue(request.getUrl().startsWith("https://restricted.googleapis.com/bigquery/v2/"));
    }

    private Properties getProperties(String pathToProp) throws IOException {
        return BQSupportFuncts
                .readFromPropFile(getClass().getResource(pathToProp).getFile());
    }

    private String getUrl(String pathToProp, String dataset) throws IOException {
        return BQSupportFuncts.constructUrlFromPropertiesFile(getProperties(pathToProp), true, dataset);
    }

}
