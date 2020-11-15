package rest.addressbook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;


import java.io.IOException;
import java.net.URI;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.junit.After;
import org.junit.Test;
import rest.addressbook.config.ApplicationConfig;
import rest.addressbook.domain.AddressBook;
import rest.addressbook.domain.Person;

/**
 * A simple test suite.
 * <ul>
 *   <li>Safe and idempotent: verify that two identical consecutive requests do not modify
 *   the state of the server.</li>
 *   <li>Not safe and idempotent: verify that only the first of two identical consecutive
 *   requests modifies the state of the server.</li>
 *   <li>Not safe nor idempotent: verify that two identical consecutive requests modify twice
 *   the state of the server.</li>
 * </ul>
 */
public class AddressBookServiceTest {

  private HttpServer server;

  @Test
  public void serviceIsAlive() throws IOException {
    // Prepare server
    AddressBook ab = new AddressBook();
    launchServer(ab);

    // Request the address book
    Client client = ClientBuilder.newClient();
    Response response = client.target("http://localhost:8282/contacts")
      .request().get();
    assertEquals(200, response.getStatus());
    assertEquals(0, response.readEntity(AddressBook.class).getPersonList()
      .size());

    //////////////////////////////////////////////////////////////////////
    // Verify that GET /contacts is well implemented by the service, i.e
    // complete the test to ensure that it is safe and idempotent
    //////////////////////////////////////////////////////////////////////

    // To check if it's safe it's needed to confirm that the state is the same
    assertEquals(0, ab.getPersonList().size());

    // To verify its idempotence  the same request must returns the same response
    Response second_response = client.target("http://localhost:8282/contacts").request().get();
    assertEquals(200, second_response.getStatus());
    assertEquals(0, second_response.readEntity(AddressBook.class).getPersonList().size());
  }

  @Test
  public void createUser() throws IOException {
    // Prepare server
    AddressBook ab = new AddressBook();
    launchServer(ab);

    // Prepare data
    Person juan = new Person();
    juan.setName("Juan");
    URI juanURI = URI.create("http://localhost:8282/contacts/person/1");

    // Create a new user
    Client client = ClientBuilder.newClient();
    Response response = client.target("http://localhost:8282/contacts")
      .request(MediaType.APPLICATION_JSON)
      .post(Entity.entity(juan, MediaType.APPLICATION_JSON));
    //New user is created
    assertEquals(201, response.getStatus());
    assertEquals(juanURI, response.getLocation());
    assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
    // Check it is the correct person
    Person juanUpdated = response.readEntity(Person.class);
    assertEquals(juan.getName(), juanUpdated.getName());
    assertEquals(1, juanUpdated.getId());
    assertEquals(juanURI, juanUpdated.getHref());

    // Check that the new user exists
    response = client.target("http://localhost:8282/contacts/person/1")
      .request(MediaType.APPLICATION_JSON).get();
    assertEquals(200, response.getStatus());
    assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());

    juanUpdated = response.readEntity(Person.class);
    assertEquals(juan.getName(), juanUpdated.getName());
    assertEquals(1, juanUpdated.getId());
    assertEquals(juanURI, juanUpdated.getHref());

    //////////////////////////////////////////////////////////////////////
    // Verify that POST /contacts is well implemented by the service, i.e
    // complete the test to ensure that it is not safe and not idempotent
    //////////////////////////////////////////////////////////////////////

    // Checking the previous state before the second response
    assertEquals(1, ab.getPersonList().size());

    // It's not idempotent, the same operation has returned different responses
    Response new_response = client.target("http://localhost:8282/contacts")
            .request(MediaType.APPLICATION_JSON)
            .post(Entity.entity(juan, MediaType.APPLICATION_JSON));
    Person new_juan = new_response.readEntity(Person.class);
    assertNotEquals(juanUpdated.getId(), new_juan.getId());

    // It's not safe due to address book's state has changed
    assertNotEquals(1, ab.getPersonList().size());

  }

  @Test
  public void createUsers() throws IOException {
    // Prepare server
    AddressBook ab = new AddressBook();
    Person salvador = new Person();
    salvador.setName("Salvador");
    salvador.setId(ab.nextId());
    ab.getPersonList().add(salvador);
    launchServer(ab);

    // Prepare data
    Person juan = new Person();
    juan.setName("Juan");
    URI juanURI = URI.create("http://localhost:8282/contacts/person/2");
    Person maria = new Person();
    maria.setName("Maria");
    URI mariaURI = URI.create("http://localhost:8282/contacts/person/3");

    // Create a user
    Client client = ClientBuilder.newClient();
    Response response = client.target("http://localhost:8282/contacts")
      .request(MediaType.APPLICATION_JSON)
      .post(Entity.entity(juan, MediaType.APPLICATION_JSON));
    assertEquals(201, response.getStatus());
    assertEquals(juanURI, response.getLocation());

    // Create a second user
    response = client.target("http://localhost:8282/contacts")
      .request(MediaType.APPLICATION_JSON)
      .post(Entity.entity(maria, MediaType.APPLICATION_JSON));
    assertEquals(201, response.getStatus());
    assertEquals(mariaURI, response.getLocation());
    assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
    Person mariaUpdated = response.readEntity(Person.class);
    assertEquals(maria.getName(), mariaUpdated.getName());
    assertEquals(3, mariaUpdated.getId());
    assertEquals(mariaURI, mariaUpdated.getHref());


    // Check that the new user exists
    response = client.target("http://localhost:8282/contacts/person/3")
      .request(MediaType.APPLICATION_JSON).get();
    assertEquals(200, response.getStatus());
    assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
    mariaUpdated = response.readEntity(Person.class);
    assertEquals(maria.getName(), mariaUpdated.getName());
    assertEquals(3, mariaUpdated.getId());
    assertEquals(mariaURI, mariaUpdated.getHref());

    //////////////////////////////////////////////////////////////////////
    // Verify that GET /contacts/person/3 is well implemented by the service, i.e
    // complete the test to ensure that it is safe and idempotent
    //////////////////////////////////////////////////////////////////////

    //Checking the previous state before get request
    assertEquals(3, ab.getPersonList().size());

    // Idempotent: The same response is received for the same request
    Response second_response = client.target("http://localhost:8282/contacts/person/3").request().get();
    assertEquals(200, second_response.getStatus());
    assertEquals(MediaType.APPLICATION_JSON_TYPE, second_response.getMediaType());
    Person new_maria = second_response.readEntity(Person.class);

    assertEquals(maria.getName(), new_maria.getName());
    assertEquals(3, new_maria.getId());
    assertEquals(mariaURI, new_maria.getHref());

    // Safe, the satus hasn't changed
    assertEquals(3, ab.getPersonList().size());
    assertEquals(maria.getName(),new_maria.getName());



  }

  @Test
  public void listUsers() throws IOException {

    // Prepare server
    AddressBook ab = new AddressBook();
    Person salvador = new Person();
    salvador.setName("Salvador");
    Person juan = new Person();
    juan.setName("Juan");
    ab.getPersonList().add(salvador);
    ab.getPersonList().add(juan);
    launchServer(ab);

    // Test list of contacts
    Client client = ClientBuilder.newClient();
    Response response = client.target("http://localhost:8282/contacts")
      .request(MediaType.APPLICATION_JSON).get();
    assertEquals(200, response.getStatus());
    assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
    AddressBook addressBookRetrieved = response
      .readEntity(AddressBook.class);
    assertEquals(2, addressBookRetrieved.getPersonList().size());
    assertEquals(juan.getName(), addressBookRetrieved.getPersonList()
      .get(1).getName());

    //////////////////////////////////////////////////////////////////////
    // Verify that GET /contacts is well implemented by the service, i.e
    // complete the test to ensure that it is safe and idempotent
    //////////////////////////////////////////////////////////////////////

    //Checking the previous state before get request
    assertEquals(2, ab.getPersonList().size());

    // Idempotent: The same response is received for the same request
    Response new_response = client.target("http://localhost:8282/contacts").request(MediaType.APPLICATION_JSON)
            .get();
    assertEquals(200, new_response.getStatus());

    assertEquals(MediaType.APPLICATION_JSON_TYPE, new_response.getMediaType());
    AddressBook bookPersons = new_response.readEntity(AddressBook.class);

    // Safe, the satus hasn't changed
    //New response
    assertEquals(2,  bookPersons.getPersonList().size());
    assertEquals(juan.getName(),  bookPersons.getPersonList().get(1).getName());

   // Previous response
    assertEquals(2, ab.getPersonList().size());
    assertEquals(juan.getName(), ab.getPersonList().get(1).getName());

  }

  @Test
  public void updateUsers() throws IOException {
    // Prepare server
    AddressBook ab = new AddressBook();
    Person salvador = new Person();
    salvador.setName("Salvador");
    salvador.setId(ab.nextId());
    Person juan = new Person();
    juan.setName("Juan");
    juan.setId(ab.getNextId());
    URI juanURI = URI.create("http://localhost:8282/contacts/person/2");
    ab.getPersonList().add(salvador);
    ab.getPersonList().add(juan);
    launchServer(ab);

    // Update Maria
    Person maria = new Person();
    maria.setName("Maria");
    Client client = ClientBuilder.newClient();
    //There is the PUT
    Response response = client
      .target("http://localhost:8282/contacts/person/2")
      .request(MediaType.APPLICATION_JSON)
      .put(Entity.entity(maria, MediaType.APPLICATION_JSON));
    //Now Juan should be called Maria

    assertEquals(200, response.getStatus());
    assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());

    Person juanUpdated = response.readEntity(Person.class);
    assertEquals(maria.getName(), juanUpdated.getName());
    assertEquals(2, juanUpdated.getId());
    assertEquals(juanURI, juanUpdated.getHref());

    // Verify that the update is real
    response = client.target("http://localhost:8282/contacts/person/2")
      .request(MediaType.APPLICATION_JSON).get();
    assertEquals(200, response.getStatus());
    assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());

    Person mariaRetrieved = response.readEntity(Person.class);
    assertEquals(maria.getName(), mariaRetrieved.getName());
    assertEquals(2, mariaRetrieved.getId());

    //System.out.println(mariaRetrieved.getHref());
    assertEquals(juanURI, mariaRetrieved.getHref());

    // Verify that only can be updated existing values
    response = client.target("http://localhost:8282/contacts/person/3")
      .request(MediaType.APPLICATION_JSON)
      .put(Entity.entity(maria, MediaType.APPLICATION_JSON));
    assertEquals(400, response.getStatus());

    //////////////////////////////////////////////////////////////////////
    // Verify that PUT /contacts/person/2 is well implemented by the service, i.e
    // complete the test to ensure that it is idempotent but not safe
    //////////////////////////////////////////////////////////////////////

    // It's not safe because now Juan is called Maria, the state has changed
    Response new_response = client.target("http://localhost:8282/contacts/person/2")
            .request(MediaType.APPLICATION_JSON).get();

    Person second_juan = new_response.readEntity(Person.class);
    //System.out.println(oldJuan.getName());
    assertEquals(second_juan.getName(), maria.getName());
    //The state has changed
    assertNotEquals(second_juan.getName(),juan.getName());

    // The same request returns the same response
    Response second_response = client
            .target("http://localhost:8282/contacts/person/2")
            .request(MediaType.APPLICATION_JSON)
            .put(Entity.entity(maria, MediaType.APPLICATION_JSON));

    Person new_mariaRetrieved = second_response.readEntity(Person.class);
    // To verify its idempotence  the same request must returns the same response
    assertEquals(200, second_response.getStatus());
    //Check names and ids
    assertEquals(mariaRetrieved.getName(), new_mariaRetrieved.getName());
    assertEquals(mariaRetrieved.getId(), new_mariaRetrieved.getId());
    // It's idempotent

  }

  @Test
  public void deleteUsers() throws IOException {
    // Prepare server
    AddressBook ab = new AddressBook();
    Person salvador = new Person();
    salvador.setName("Salvador");
    salvador.setId(1);
    Person juan = new Person();
    juan.setName("Juan");
    juan.setId(2);
    ab.getPersonList().add(salvador);
    ab.getPersonList().add(juan);
    launchServer(ab);

    // Delete a user
    Client client = ClientBuilder.newClient();
    Response response = client
      .target("http://localhost:8282/contacts/person/2").request()
      .delete();
    assertEquals(204, response.getStatus());

    // Verify that the user has been deleted
    response = client.target("http://localhost:8282/contacts/person/2")
      .request().delete();
    assertEquals(404, response.getStatus());

    //////////////////////////////////////////////////////////////////////
    // Verify that DELETE /contacts/person/2 is well implemented by the service, i.e
    // complete the test to ensure that it is idempotent but not safe
    //////////////////////////////////////////////////////////////////////

    // BUG FOUND
    // It's not safe because the state has changed
    assertNotEquals(2, ab.getPersonList().size());

    // Idempotent?: As we could see previously the response code was 204
    Response new_response = client.target("http://localhost:8282/contacts/person/2").request().delete();

    //However now the code we are receiving it has changed, same request differente response
    assertNotEquals(204, new_response.getStatus());
    // Instead, we are getting 404, so it's not idempotent

    assertNotEquals(404, new_response.getStatus());

    // 404 The requested resource could not be found but may be available in the future.
    // 204 The server successfully processed the request, and is not returning any content.

  }

  @Test
  public void findUsers() throws IOException {
    // Prepare server
    AddressBook ab = new AddressBook();
    Person salvador = new Person();
    salvador.setName("Salvador");
    salvador.setId(1);
    Person juan = new Person();
    juan.setName("Juan");
    juan.setId(2);
    ab.getPersonList().add(salvador);
    ab.getPersonList().add(juan);
    launchServer(ab);

    // Test user 1 exists
    Client client = ClientBuilder.newClient();
    Response response = client
      .target("http://localhost:8282/contacts/person/1")
      .request(MediaType.APPLICATION_JSON).get();
    assertEquals(200, response.getStatus());
    assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
    Person person = response.readEntity(Person.class);
    assertEquals(person.getName(), salvador.getName());
    assertEquals(person.getId(), salvador.getId());
    assertEquals(person.getHref(), salvador.getHref());

    // Test user 2 exists
    response = client.target("http://localhost:8282/contacts/person/2")
      .request(MediaType.APPLICATION_JSON).get();
    assertEquals(200, response.getStatus());
    assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
    person = response.readEntity(Person.class);
    assertEquals(person.getName(), juan.getName());
    assertEquals(2, juan.getId());
    assertEquals(person.getHref(), juan.getHref());

    // Test user 3 exists
    response = client.target("http://localhost:8282/contacts/person/3")
      .request(MediaType.APPLICATION_JSON).get();
    assertEquals(404, response.getStatus());
  }

  private void launchServer(AddressBook ab) throws IOException {
    URI uri = UriBuilder.fromUri("http://localhost/").port(8282).build();
    server = GrizzlyHttpServerFactory.createHttpServer(uri,
      new ApplicationConfig(ab));
    server.start();
  }

  @After
  public void shutdown() {
    if (server != null) {
      server.shutdownNow();
    }
    server = null;
  }

}
