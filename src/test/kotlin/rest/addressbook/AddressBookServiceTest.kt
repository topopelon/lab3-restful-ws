package rest.addressbook

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
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
        Mockito.clearAllCaches()
        //personListSpy = Mockito.spy(addressBook.personList)
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
            assertEquals(HttpStatus.OK, response.statusCode)
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
        // Prepare data
        val juan = Person(name = "Juan")

        // Tests user creation
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
            // [response] change every same POST request -> not idempotent
            // POST request change [personList] resource at the server everytime -> not safe
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

        // Check [people] was introduced correctly
        assertEquals(1, addressBook.personList.size)

        // Prepare data
        val people = listOf(
            Person(name = "Juan"),
            Person(name = "Pedro"),
            Person(name = "Maria"),
            Person(name = "Carmen")
        )
        // Tests users creation
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
                // [response] change every same POST request -> not idempotent
                // POST request change [personList] resource at the server everytime -> not safe
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
        val people = listOf(
            Person(name = "Juan", id = addressBook.nextId()),
            Person(name = "Pedro", id = addressBook.nextId()),
            Person(name = "Maria", id = addressBook.nextId()),
            Person(name = "Carmen", id = addressBook.nextId())
        )
        people.forEach { person ->
            addressBook.personList.add(person)
        }

        // Check [people] was introduced correctly
        assertEquals(people.size, addressBook.personList.size)

        // Test list of contacts
        val n = (1..3)
        n.forEach { _ ->
            val response =
                restTemplate.getForEntity(
                    "http://localhost:$port/contacts",
                    Array<Person>::class.java
                )
            people.forEachIndexed { i, person ->
                val personURI = null
                // Check headers
                assertEquals(HttpStatus.OK, response.statusCode)
                assertEquals(MediaType.APPLICATION_JSON, response.headers.contentType)
                // [response] doesn't change every same GET request -> idempotent
                // GET request doesn't change [personList] resource at the server everytime -> not safe
                // Check [response]
                assertEquals(people.size, response.body?.size)
                assertPersonInBody(person, person.id, response.body?.get(i), personURI)
                // Check [personList]
                assertPersonInPersonList(person, person.id, personURI)
            }
            // Check if [personList] is [people] size
            assertEquals(people.size, addressBook.personList.size)
        }

        //////////////////////////////////////////////////////////////////////
        // Verify that GET /contacts is well implemented by the service, i.e
        // complete the test to ensure that it is safe and idempotent
        //////////////////////////////////////////////////////////////////////
    }

    @Test
    fun updateUsers() {
        // Prepare server
        val people = listOf(
            Person(name = "Salvador", id = addressBook.nextId()),
            Person(name = "Juan", id = addressBook.nextId()),
        )

        people.forEach { person ->
            addressBook.personList.add(person)
        }

        // Check [people] was introduced correctly
        assertEquals(people.size, addressBook.personList.size)

        // Get [juan] info
        val juan = people.last()
        val juanURI = URI.create("http://localhost:$port/contacts/person/${juan.id}")

        // Update Maria
        val maria = Person(name = "Maria")

        val n = (1..3)
        n.forEach { _ ->
            var response =
                restTemplate.exchange(
                    juanURI,
                    HttpMethod.PUT,
                    HttpEntity(maria),
                    Person::class.java
                )
            assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
            // Verify that the update is real
            response = restTemplate.getForEntity(juanURI, Person::class.java)
            // [response] doesn't change every same PUT request -> idempotent
            // PUT request change [personList] resource at the server everytime -> not safe
            val pretendUpdatedMaria = Person(name = maria.name, id = juan.id, href = juanURI)
            assertPerson(response, pretendUpdatedMaria, juan.id, HttpStatus.OK)

        }

        // Verify that only can be updated existing values
        restTemplate.execute("http://localhost:$port/contacts/person/${people.size + 1}",
            HttpMethod.PUT,
            {
                it.headers.contentType = MediaType.APPLICATION_JSON
                people.forEach { person ->
                    ObjectMapper().writeValue(it.body, person)
                }
            },
            { assertEquals(HttpStatus.NOT_FOUND, it.statusCode) }
        )

        //////////////////////////////////////////////////////////////////////
        // Verify that PUT /contacts/person/2 is well implemented by the service, i.e
        // complete the test to ensure that it is idempotent but not safe
        //////////////////////////////////////////////////////////////////////
    }

    @Test
    fun deleteUsers() {
        // Prepare server
        val people = listOf(
            Person(name = "Salvador", id = addressBook.nextId()),
            Person(name = "Juan", id = addressBook.nextId()),
        )

        people.forEach { person ->
            addressBook.personList.add(person)
        }
        // Check [people] was introduced correctly
        assertEquals(people.size, addressBook.personList.size)

        // Tests users deletion
        people.forEach { person ->
            val personUri = URI.create("http://localhost:$port/contacts/person/${person.id}")
            // Delete a user
            restTemplate.execute(
                personUri,
                HttpMethod.DELETE,
                {},
                { assertEquals(HttpStatus.NO_CONTENT, it.statusCode) })
            (1..3).forEach { _ ->
                // Verify that the user has been deleted
                restTemplate.execute(
                    personUri,
                    HttpMethod.GET,
                    {},
                    { assertEquals(HttpStatus.NOT_FOUND, it.statusCode) })
                // [response] change every same DELETE request -> idempotent
                // DELETE request change [personList] resource at the server everytime -> not safe
                // Check if it is not in [personList]
                val personFoundList = addressBook.personList.filter {
                    it.name == person.name && it.id == person.id
                }
                assertEquals(0, personFoundList.size)
            }
        }
        assertEquals(0, addressBook.personList.size)

        //////////////////////////////////////////////////////////////////////
        // Verify that DELETE /contacts/person/2 is well implemented by the service, i.e
        // complete the test to ensure that it is idempotent but not safe
        //////////////////////////////////////////////////////////////////////
    }

    @Test
    fun findUsers() {
        // Prepare server
        val people = listOf(
            Person(name = "Salvador", id = addressBook.nextId()),
            Person(name = "Juan", id = addressBook.nextId()),
        )

        val peoplesUri: MutableList<URI> = mutableListOf()

        people.forEach { person ->
            addressBook.personList.add(person)
            peoplesUri.add(URI.create("http://localhost:$port/contacts/person/${person.id}"))
        }

        // Check [people] was introduced correctly
        assertEquals(people.size, addressBook.personList.size)

        // Test users in [people] exist
        (people.indices).forEach { i ->
            val person = people[i]
            val personURI = null
            val response = restTemplate.getForEntity(peoplesUri[i], Person::class.java)
            // Check [response] body
            assertPersonInBody(person, person.id, response.body, personURI)
            // Check [personList]
            assertPersonInPersonList(person, person.id, personURI)
        }
        // Check [personList] is empty
        assertEquals(people.size, addressBook.personList.size)

        // Test last user doesn't exist
        restTemplate.execute(
            "http://localhost:$port/contacts/person/${people.size + 1}",
            HttpMethod.GET,
            {},
            { assertEquals(HttpStatus.NOT_FOUND, it.statusCode) })
    }

    /* ---- Private methods ---- */

    /**
     * Checks if [person] is found whether in [response] body or in the personList server database.
     * also checks the [response] headers
     */
    private fun assertPerson(
        response: ResponseEntity<Person>,
        person: Person,
        id: Int,
        httpStatus: HttpStatus
    ) {
        val uri = URI.create("http://localhost:$port/contacts/person/$id")
        // Check response headers
        assertEquals(httpStatus, response.statusCode)
        assertEquals(MediaType.APPLICATION_JSON, response.headers.contentType)

        assertPersonInPersonList(person, id, uri)
        assertPersonInBody(person, id, response.body, uri)
    }

    /**
     * Checks if [person] is found in personList server database
     */
    private fun assertPersonInPersonList(person: Person, id: Int, uri: URI?) {
        // Find in [personList] at the server database
        val personListUpdated = addressBook.personList[id - 1]
        assertEquals(person.name, personListUpdated.name)
        assertEquals(id, personListUpdated.id)
        assertEquals(uri, personListUpdated.href)
    }

    /**
     * Checks if [person] is equal to changed [personUpdated] in the response body
     */
    private fun assertPersonInBody(person: Person, id: Int, personUpdated: Person?, uri: URI?) {
        // Check response body
        assertEquals(person.name, personUpdated?.name)
        assertEquals(id, personUpdated?.id)
        assertEquals(uri, personUpdated?.href)
    }

}
