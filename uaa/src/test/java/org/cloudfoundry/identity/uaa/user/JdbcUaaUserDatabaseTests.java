package org.cloudfoundry.identity.uaa.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.UUID;

import org.cloudfoundry.identity.uaa.scim.ScimUser;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * @author Luke Taylor
 */
public class JdbcUaaUserDatabaseTests {

	private static JdbcTemplate template;
	private JdbcUaaUserDatabase db;

	private static final String JOE_ID = "550e8400-e29b-41d4-a716-446655440000";

	private static final String MABEL_ID = UUID.randomUUID().toString();

	@BeforeClass
	public static void createDatasource() throws Exception {
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setDriverClassName("org.hsqldb.jdbcDriver");
		dataSource.setUrl("jdbc:hsqldb:mem:jdbcUaaTests");
		dataSource.setUsername("sa");
		dataSource.setPassword("");

		template = new JdbcTemplate(dataSource);
	}

	@Before
	public void initializeDb() throws Exception {
		db = new JdbcUaaUserDatabase(template);
		template.execute("create table users(" +
				"id char(36) not null primary key," +
				"username varchar(20) not null," +
				"password varchar(20) not null," +
				"email varchar(20) not null," +
				"givenName varchar(20) not null," +
				"familyName varchar(20) not null," +
				"created timestamp default current_timestamp," +
				"lastModified timestamp default current_timestamp," +
				"constraint unique_uk_1 unique(username)" +
			")");
		template.execute("insert into users (id, username, password, email, givenName, familyName) " +
				 "values ('"+ JOE_ID + "', 'joe','joespassword','joe@joe.com','Joe','User')");
		template.execute("insert into users (id, username, password, email, givenName, familyName) " +
				 "values ('"+ MABEL_ID + "', 'mabel','mabelspassword','mabel@mabel.com','Mabel','User')");
	}

	@After
	public void clearDb() throws Exception {
		template.execute("drop table users");
	}

	@AfterClass
	public static void shutDownDb() {
		template.execute("SHUTDOWN");
		template = null;
	}

	@Test
	public void canCreateUser() {
		ScimUser user = new ScimUser(null, "josephine", "Jo", "User");
		user.addEmail("jo@blah.com");
		db.createUser(user, "password");
	}

	@Test
	public void updateModifiesExpectedData() {
		ScimUser jo = new ScimUser(null, "josephine", "Jo", "NewUser");
		jo.addEmail("jo@blah.com");

		ScimUser joe = db.updateUser(JOE_ID, jo);

		// Can't change username (yet)
		assertEquals("joe", joe.getUserName());
		assertEquals("jo@blah.com", joe.getPrimaryEmail());
		assertEquals("Jo", joe.getGivenName());
		assertEquals("NewUser", joe.getFamilyName());
	}

	@Test
	public void canRetrieveExistingUser() {
		ScimUser joe = db.retrieveUser(JOE_ID);
		assertJoe(joe);
	}

	private void assertJoe(ScimUser joe) {
		assertNotNull(joe);
		assertEquals(JOE_ID, joe.getId());
		assertEquals("Joe", joe.getGivenName());
		assertEquals("User", joe.getFamilyName());
		assertEquals("joe@joe.com", joe.getPrimaryEmail());
		assertEquals("joe", joe.getUserName());
	}

	@Test
	public void getValidUserSucceeds() {
		UaaUser joe = db.retrieveUserByName("joe");
		assertNotNull(joe);
		assertEquals(JOE_ID, joe.getId());
		assertEquals("joe", joe.getUsername());
		assertEquals("joe@joe.com", joe.getEmail());
		assertEquals("joespassword", joe.getPassword());
	}

	@Test(expected = UsernameNotFoundException.class)
	public void getNonExistentUserRaisedNotFoundException() {
		db.retrieveUserByName("jo");
	}

	@Test
	public void canRemoveExistingUser() {
		ScimUser joe = db.removeUser(JOE_ID);
		assertJoe(joe);
		template.queryForList("select * from users").isEmpty();
	}

	@Test
	public void canRetrieveUsers() {
		assertEquals(2, db.retrieveUsers().size());
	}

	@Test
	public void canRetrieveUsersWithFilterExists() {
		assertEquals(2, db.retrieveUsers("userName pr").size());
	}

	@Test
	public void canRetrieveUsersWithFilterEquals() {
		assertEquals(1, db.retrieveUsers("userName eq 'joe'").size());
	}

	@Test
	public void canRetrieveUsersWithFilterContains() {
		assertEquals(2, db.retrieveUsers("userName co 'e'").size());
	}

	@Test
	public void canRetrieveUsersWithFilterStartsWith() {
		assertEquals(1, db.retrieveUsers("userName sw 'j'").size());
	}

	@Test
	public void canRetrieveUsersWithEmailFilter() {
		assertEquals(1, db.retrieveUsers("emails.value sw 'j'").size());
	}

	@Test
	public void canRetrieveUsersWithFilterBooleanAnd() {
		assertEquals(2, db.retrieveUsers("userName pr and emails.value co '.com'").size());
	}

	@Test
	public void canRetrieveUsersWithFilterBooleanOr() {
		assertEquals(2, db.retrieveUsers("userName eq 'joe' or emails.value co '.com'").size());
	}

	@Test(expected=UnsupportedOperationException.class)
	public void canRetrieveUsersWithIllegalFilter() {
		assertEquals(2, db.retrieveUsers("emails.type eq 'bar'").size());
	}

}
