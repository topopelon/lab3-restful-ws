package rest.addressbook

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.*
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.http.*
import java.net.URI

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AddressBookServiceTest {

    @LocalServerPort
    var port = 0

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @BeforeEach
    fun cleanRepository() {
        addressBook.clear()
    }

    @Test
    fun serviceIsAlive() {
        // Check if [personList] is empty before GET request
        assertEquals(0, addressBook.personList.size)
        // Make GET request [n] times to make sure
        (1..3).forEach { _ ->
            val response = restTemplate.getForEntity(
                "http://localhost:$port/contacts",
                Array<Person>::class.java
            )
            assertEquals(MediaType.APPLICATION_JSON, response.headers.contentType)
            assertEquals(200, response.statusCode.value())
            assertEquals(0, response.body?.size)
            assertEquals(0, addressBook.personList.size)
        }
        // Check if [personList] remains empty
        assertEquals(0, addressBook.personList.size)

        //////////////////////////////////////////////////////////////////////
        // Verify that GET /contacts is well implemented by the service, i.e
        // complete the test to ensure that it is safe and idempotent
        //////////////////////////////////////////////////////////////////////
    }

    @Test
    fun createUser() {
        // Check if [personList] is empty before GET request
        assertEquals(0, addressBook.personList.size)
        // Prepare data
        val juan = Person(name = "Juan")
        // Every loop information is different
        val n = (1..3)
        n.forEach { i ->
            // POST [juan] [n] times
            var response = restTemplate.postForEntity(
                "http://localhost:$port/contacts", juan, Person::class.java
            )
            // Check person has been created
            val juanURI: URI = URI.create("http://localhost:$port/contacts/person/$i")
            assertEquals(juanURI, response.headers.location)
            assertPerson(response, juan, i, HttpStatus.CREATED)
            // Check that the new user exists
            response = restTemplate.getForEntity(juanURI, Person::class.java)
            assertPerson(response, juan, i, HttpStatus.OK)
            assertEquals(i, addressBook.personList.size)
        }

        assertEquals(n.toList().size, addressBook.personList.size)

        //////////////////////////////////////////////////////////////////////
        // Verify that POST /contacts is well implemented by the service, i.e
        // complete the test to ensure that it is not safe and not idempotent
        //////////////////////////////////////////////////////////////////////
    }

    @Test
    fun createUsers() {
        // Prepare server
        val salvador = Person(id = addressBook.nextId(), name = "Salvador")
        addressBook.personList.add(salvador)

        // Prepare data
        val people = List(4) {
            Person(name = "Juan");
            Person(name = "Pedro");
            Person(name = "Maria");
            Person(name = "Carmen")
        }
        // Loop
        people.forEachIndexed { i, person ->
            val personURI = URI.create(
                "http://localhost:$port/contacts/person/${i + 2}"
            )
            // Create user
            var response =
                restTemplate.postForEntity(
                    "http://localhost:$port/contacts",
                    person,
                    Person::class.java
                )
            // Check person has been created
            assertEquals(personURI, response.headers.location)
            assertPerson(response, person, i + 2, HttpStatus.CREATED)
            // Check that the new user exists
            (1..3).forEach { _ ->
                response = restTemplate.getForEntity(personURI, Person::class.java)
                assertPerson(response, person, i + 2, HttpStatus.OK)
                assertEquals(i + 2, addressBook.personList.size)
            }
        }

        // Check if [personList] is [people] size
        assertEquals(people.size + 1, addressBook.personList.size)

        //////////////////////////////////////////////////////////////////////
        // Verify that GET /contacts/person/3 is well implemented by the service, i.e
        // complete the test to ensure that it is safe and idempotent
        //////////////////////////////////////////////////////////////////////
    }

    @Test
    fun listUsers() {

        // Prepare server
        val people = List(4) {
            Person(name = "Juan", id = addressBook.nextId());
            Person(name = "Pedro", id = addressBook.nextId());
            Person(name = "Maria", id = addressBook.nextId());
            Person(name = "Carmen", id = addressBook.nextId())
        }
        people.forEach { person ->
            addressBook.personList.add(person)
        }

        // Test list of contacts
        val response =
            restTemplate.getForEntity("http://localhost:$port/contacts", Array<Person>::class.java)
        people.forEachIndexed { i, person ->
            assertEquals(200, response.statusCode.value())
            assertEquals(MediaType.APPLICATION_JSON, response.headers.contentType)
            assertEquals(people.size, response.body?.size)
            assertEquals(person.name, response.body?.get(i)?.name)
        }

        //////////////////////////////////////////////////////////////////////
        // Verify that GET /contacts is well implemented by the service, i.e
        // complete the test to ensure that it is safe and idempotent
        //////////////////////////////////////////////////////////////////////
    }

    @Test
    fun updateUsers() {
        // Prepare server
        val salvador = Person(name = "Salvador", id = addressBook.nextId())
        val juan = Person(name = "Juan", id = addressBook.nextId())
        val juanURI = URI.create("http://localhost:$port/contacts/person/2")
        addressBook.personList.add(salvador)
        addressBook.personList.add(juan)

        // Update Maria
        val maria = Person(name = "Maria")

        var response =
            restTemplate.exchange(juanURI, HttpMethod.PUT, HttpEntity(maria), Person::class.java)
        assertEquals(204, response.statusCode.value())

        // Verify that the update is real
        response = restTemplate.getForEntity(juanURI, Person::class.java)
        assertEquals(200, response.statusCode.value())
        assertEquals(MediaType.APPLICATION_JSON, response.headers.contentType)
        val updatedMaria = response.body
        assertEquals(maria.name, updatedMaria?.name)
        assertEquals(2, updatedMaria?.id)
        assertEquals(juanURI, updatedMaria?.href)

        // Verify that only can be updated existing values
        restTemplate.execute("http://localhost:$port/contacts/person/3", HttpMethod.PUT,
            {
                it.headers.contentType = MediaType.APPLICATION_JSON
                ObjectMapper().writeValue(it.body, maria)
            },
            { assertEquals(404, it.statusCode.value()) }
        )

        //////////////////////////////////////////////////////////////////////
        // Verify that PUT /contacts/person/2 is well implemented by the service, i.e
        // complete the test to ensure that it is idempotent but not safe
        //////////////////////////////////////////////////////////////////////
    }

    @Test
    fun deleteUsers() {
        // Prepare server
        val salvador = Person(name = "Salvador", id = addressBook.nextId())
        val juan = Person(name = "Juan", id = addressBook.nextId())
        val juanURI = URI.create("http://localhost:$port/contacts/person/2")
        addressBook.personList.add(salvador)
        addressBook.personList.add(juan)

        // Delete a user
        restTemplate.execute(
            juanURI,
            HttpMethod.DELETE,
            {},
            { assertEquals(204, it.statusCode.value()) })

        // Verify that the user has been deleted
        restTemplate.execute(
            juanURI,
            HttpMethod.GET,
            {},
            { assertEquals(404, it.statusCode.value()) })

        //////////////////////////////////////////////////////////////////////
        // Verify that DELETE /contacts/person/2 is well implemented by the service, i.e
        // complete the test to ensure that it is idempotent but not safe
        //////////////////////////////////////////////////////////////////////
    }

    @Test
    fun findUsers() {
        // Prepare server
        val salvador = Person(name = "Salvador", id = addressBook.nextId())
        val juan = Person(name = "Juan", id = addressBook.nextId())
        val salvadorURI = URI.create("http://localhost:$port/contacts/person/1")
        val juanURI = URI.create("http://localhost:$port/contacts/person/2")
        addressBook.personList.add(salvador)
        addressBook.personList.add(juan)

        // Test user 1 exists
        var response = restTemplate.getForEntity(salvadorURI, Person::class.java)
        assertEquals(200, response.statusCode.value())
        assertEquals(MediaType.APPLICATION_JSON, response.headers.contentType)
        var person = response.body
        assertEquals(salvador.name, person?.name)
        assertEquals(salvador.id, person?.id)
        assertEquals(salvador.href, person?.href)

        // Test user 2 exists
        response = restTemplate.getForEntity(juanURI, Person::class.java)
        assertEquals(200, response.statusCode.value())
        assertEquals(MediaType.APPLICATION_JSON, response.headers.contentType)
        person = response.body
        assertEquals(juan.name, person?.name)
        assertEquals(juan.id, person?.id)
        assertEquals(juan.href, person?.href)

        // Test user 3 doesn't exist
        restTemplate.execute(
            "http://localhost:$port/contacts/person/3",
            HttpMethod.GET,
            {},
            { assertEquals(404, it.statusCode.value()) })
    }

    /* ---- Private methods ---- */

    private fun assertPerson(
        response: ResponseEntity<Person>,
        person: Person,
        id: Int,
        httpStatus: HttpStatus
    ) {
        val uri = URI.create("http://localhost:$port/contacts/person/$id")
        assertEquals(httpStatus, response.statusCode)
        assertEquals(MediaType.APPLICATION_JSON, response.headers.contentType)
        val personUpdated = response.body
        assertEquals(person.name, personUpdated?.name)
        assertEquals(id, personUpdated?.id)
        assertEquals(uri, personUpdated?.href)
    }

}
