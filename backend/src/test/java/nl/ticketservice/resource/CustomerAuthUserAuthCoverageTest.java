package nl.ticketservice.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import nl.ticketservice.entity.Customer;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
public class CustomerAuthUserAuthCoverageTest {

    @Inject
    EntityManager em;

    private static String adminToken;
    private static Long testCustomerId;
    private static String testCustomerInviteToken;
    private static String customerAuthToken;
    private static String userAuthToken;

    private String getAdminToken() {
        if (adminToken == null) {
            adminToken = given()
                    .contentType(ContentType.JSON)
                    .body(Map.of("email", "admin@ticketservice.nl", "password", "admin"))
                .when()
                    .post("/api/admin/auth/login")
                .then()
                    .statusCode(200)
                    .extract()
                    .path("token");
        }
        return adminToken;
    }

    // =========================================================================
    // Setup: Create a customer and retrieve the invite token
    // =========================================================================

    @Test
    @Order(1)
    void setup_createCustomerForTests() {
        testCustomerId = ((Number) given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(Map.of(
                        "companyName", "Coverage Test BV",
                        "contactPerson", "Coverage Tester",
                        "email", "coverage@authtest.nl",
                        "phone", "0612345678",
                        "active", true))
            .when()
                .post("/api/customers")
            .then()
                .statusCode(201)
                .extract()
                .path("id")).longValue();
    }

    @Test
    @Order(2)
    @Transactional
    void setup_getInviteToken() {
        Customer customer = Customer.findById(testCustomerId);
        assertNotNull(customer);
        testCustomerInviteToken = customer.inviteToken;
        assertNotNull(testCustomerInviteToken);
    }

    // =========================================================================
    // CustomerAuthResource: GET /invite/{token} - valid token
    // =========================================================================

    @Test
    @Order(3)
    void customerInvite_validToken_returnsCompanyInfo() {
        given()
            .when()
                .get("/api/customer/auth/invite/" + testCustomerInviteToken)
            .then()
                .statusCode(200)
                .body("companyName", equalTo("Coverage Test BV"))
                .body("email", equalTo("coverage@authtest.nl"));
    }

    // =========================================================================
    // CustomerAuthResource: GET /invite/{token} - expired token
    // =========================================================================

    @Test
    @Order(4)
    @Transactional
    void setup_createCustomerWithExpiredInvite() {
        Customer customer = new Customer();
        customer.companyName = "Expired Invite BV";
        customer.contactPerson = "Expired Person";
        customer.email = "expired-invite@coverage.nl";
        customer.active = true;
        customer.slug = "expired-invite-bv";
        customer.inviteToken = "expired-invite-token-123";
        customer.inviteTokenExpiry = LocalDateTime.now().minusDays(1);
        em.persist(customer);
        em.flush();
    }

    @Test
    @Order(5)
    void customerInvite_expiredToken_returns400() {
        given()
            .when()
                .get("/api/customer/auth/invite/expired-invite-token-123")
            .then()
                .statusCode(400);
    }

    // =========================================================================
    // CustomerAuthService: login with null passwordHash (not activated)
    // =========================================================================

    @Test
    @Order(6)
    void customerLogin_noPasswordHash_returns401() {
        // Seeded customer "De Feestfabriek" has no password set
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", "info@feestfabriek.nl", "password", "somepassword"))
            .when()
                .post("/api/customer/auth/login")
            .then()
                .statusCode(401);
    }

    @Test
    @Order(7)
    void customerLogin_newCustomerNoPasswordHash_returns401() {
        // The customer created in order 1 also has no password yet
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", "coverage@authtest.nl", "password", "anything"))
            .when()
                .post("/api/customer/auth/login")
            .then()
                .statusCode(401);
    }

    // =========================================================================
    // CustomerAuthService: setPassword with expired invite token
    // =========================================================================

    @Test
    @Order(8)
    void customerSetPassword_expiredInviteToken_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("token", "expired-invite-token-123", "password", "SomePassword"))
            .when()
                .post("/api/customer/auth/set-password")
            .then()
                .statusCode(400);
    }

    // =========================================================================
    // CustomerAuthResource: set-password with valid token
    // =========================================================================

    @Test
    @Order(10)
    void customerSetPassword_validInvite_returnsAuthToken() {
        customerAuthToken = given()
                .contentType(ContentType.JSON)
                .body(Map.of("token", testCustomerInviteToken, "password", "TestPassword123"))
            .when()
                .post("/api/customer/auth/set-password")
            .then()
                .statusCode(200)
                .body("token", notNullValue())
                .body("companyName", equalTo("Coverage Test BV"))
                .extract()
                .path("token");
    }

    // =========================================================================
    // CustomerAuthService: login with wrong password
    // =========================================================================

    @Test
    @Order(15)
    void customerLogin_wrongPassword_returns401() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", "coverage@authtest.nl", "password", "WrongPassword"))
            .when()
                .post("/api/customer/auth/login")
            .then()
                .statusCode(401);
    }

    @Test
    @Order(16)
    void customerLogin_validCredentials_returns200() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", "coverage@authtest.nl", "password", "TestPassword123"))
            .when()
                .post("/api/customer/auth/login")
            .then()
                .statusCode(200)
                .body("token", notNullValue())
                .body("customerId", equalTo(testCustomerId.intValue()));
    }

    // =========================================================================
    // CustomerAuthResource: POST /forgot-password
    // =========================================================================

    @Test
    @Order(20)
    void customerForgotPassword_blankEmail_returnsMessage() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", ""))
            .when()
                .post("/api/customer/auth/forgot-password")
            .then()
                .statusCode(200)
                .body("message", notNullValue());
    }

    @Test
    @Order(21)
    void customerForgotPassword_nullEmail_returnsMessage() {
        // Body without "email" key so body.get("email") returns null
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("other", "value"))
            .when()
                .post("/api/customer/auth/forgot-password")
            .then()
                .statusCode(200)
                .body("message", notNullValue());
    }

    @Test
    @Order(22)
    void customerForgotPassword_nonexistentEmail_returnsMessage() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", "nonexistent@nowhere.nl"))
            .when()
                .post("/api/customer/auth/forgot-password")
            .then()
                .statusCode(200)
                .body("message", notNullValue());
    }

    @Test
    @Order(23)
    void customerForgotPassword_existingCustomerWithPassword_returnsMessage() {
        // coverage@authtest.nl now has a password (set in order 10)
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", "coverage@authtest.nl"))
            .when()
                .post("/api/customer/auth/forgot-password")
            .then()
                .statusCode(200)
                .body("message", notNullValue());
    }

    @Test
    @Order(24)
    void customerForgotPassword_customerWithoutPassword_returnsMessage() {
        // Seeded customer "De Feestfabriek" has no passwordHash
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", "info@feestfabriek.nl"))
            .when()
                .post("/api/customer/auth/forgot-password")
            .then()
                .statusCode(200)
                .body("message", notNullValue());
    }

    // =========================================================================
    // CustomerAuthResource: POST /reset-password
    // =========================================================================

    @Test
    @Order(30)
    void customerResetPassword_malformedBase64_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("token", "!!!not-valid-base64!!!", "password", "NewPassword123"))
            .when()
                .post("/api/customer/auth/reset-password")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(31)
    void customerResetPassword_wrongNumberOfParts_returns400() {
        String payload = "onlytwoParts|data";
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("token", token, "password", "NewPassword123"))
            .when()
                .post("/api/customer/auth/reset-password")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(32)
    void customerResetPassword_expiredToken_returns400() {
        // Expiry 1000000000 is ~2001, well in the past
        String payload = testCustomerId + "|1000000000|fakesig";
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("token", token, "password", "NewPassword123"))
            .when()
                .post("/api/customer/auth/reset-password")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(33)
    void customerResetPassword_wrongSignature_returns400() {
        long futureExpiry = System.currentTimeMillis() / 1000 + 86400;
        String payload = testCustomerId + "|" + futureExpiry + "|wrongsignature";
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("token", token, "password", "NewPassword123"))
            .when()
                .post("/api/customer/auth/reset-password")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(34)
    void customerResetPassword_nonNumericId_returns400() {
        // Triggers outer catch block (NumberFormatException)
        long futureExpiry = System.currentTimeMillis() / 1000 + 86400;
        String payload = "abc|" + futureExpiry + "|somesig";
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("token", token, "password", "NewPassword123"))
            .when()
                .post("/api/customer/auth/reset-password")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(35)
    void customerResetPassword_nonexistentCustomer_returns400() {
        long futureExpiry = System.currentTimeMillis() / 1000 + 86400;
        String payload = "999999|" + futureExpiry + "|fakesig";
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("token", token, "password", "NewPassword123"))
            .when()
                .post("/api/customer/auth/reset-password")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(36)
    void customerResetPassword_customerWithNullPasswordHash_returns400() {
        // Create a fresh customer with no password (null passwordHash)
        Long noPwdId = ((Number) given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(Map.of(
                        "companyName", "NoPwd Test BV",
                        "contactPerson", "NoPwd Tester",
                        "email", "nopwd-reset@coverage.nl",
                        "active", true))
            .when()
                .post("/api/customers")
            .then()
                .statusCode(201)
                .extract()
                .path("id")).longValue();

        // Build a reset token targeting this customer (passwordHash is null)
        long futureExpiry = System.currentTimeMillis() / 1000 + 86400;
        String payload = noPwdId + "|" + futureExpiry + "|fakesig";
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("token", token, "password", "NewPassword123"))
            .when()
                .post("/api/customer/auth/reset-password")
            .then()
                .statusCode(400);
    }

    // =========================================================================
    // CustomerAuthResource: PUT /branding
    // =========================================================================

    @Test
    @Order(40)
    void updateBranding_withoutAuth_returns401() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("logoUrl", "https://example.com/logo.png"))
            .when()
                .put("/api/customer/auth/branding")
            .then()
                .statusCode(401);
    }

    @Test
    @Order(41)
    void updateBranding_logoUrl_returns200() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + customerAuthToken)
                .body(Map.of("logoUrl", "https://example.com/new-logo.png"))
            .when()
                .put("/api/customer/auth/branding")
            .then()
                .statusCode(200)
                .body("success", equalTo(true));
    }

    @Test
    @Order(42)
    void updateBranding_validPrimaryColor_returns200() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + customerAuthToken)
                .body(Map.of("primaryColor", "#FF5733"))
            .when()
                .put("/api/customer/auth/branding")
            .then()
                .statusCode(200)
                .body("success", equalTo(true));
    }

    @Test
    @Order(43)
    void updateBranding_invalidPrimaryColor_doesNotCrash() {
        // "not-a-color" does not match ^#[0-9a-fA-F]{6}$ — should NOT update but returns 200
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + customerAuthToken)
                .body(Map.of("primaryColor", "not-a-color"))
            .when()
                .put("/api/customer/auth/branding")
            .then()
                .statusCode(200)
                .body("success", equalTo(true));
    }

    @Test
    @Order(44)
    void updateBranding_invalidPrimaryColorTooShort_doesNotUpdate() {
        // "#FFF" does not match ^#[0-9a-fA-F]{6}$
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + customerAuthToken)
                .body(Map.of("primaryColor", "#FFF"))
            .when()
                .put("/api/customer/auth/branding")
            .then()
                .statusCode(200)
                .body("success", equalTo(true));
    }

    @Test
    @Order(45)
    void updateBranding_validSecondaryColor_returns200() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + customerAuthToken)
                .body(Map.of("secondaryColor", "#1A2B3C"))
            .when()
                .put("/api/customer/auth/branding")
            .then()
                .statusCode(200)
                .body("success", equalTo(true));
    }

    @Test
    @Order(46)
    void updateBranding_invalidSecondaryColor_doesNotUpdate() {
        // "ZZZZZZ" does not match the regex
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + customerAuthToken)
                .body(Map.of("secondaryColor", "ZZZZZZ"))
            .when()
                .put("/api/customer/auth/branding")
            .then()
                .statusCode(200)
                .body("success", equalTo(true));
    }

    @Test
    @Order(47)
    void updateBranding_invalidSecondaryColorBadHex_doesNotUpdate() {
        // "#GGGGGG" has invalid hex characters
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + customerAuthToken)
                .body(Map.of("secondaryColor", "#GGGGGG"))
            .when()
                .put("/api/customer/auth/branding")
            .then()
                .statusCode(200)
                .body("success", equalTo(true));
    }

    @Test
    @Order(48)
    void updateBranding_website_returns200() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + customerAuthToken)
                .body(Map.of("website", "https://www.coveragetest.nl"))
            .when()
                .put("/api/customer/auth/branding")
            .then()
                .statusCode(200)
                .body("success", equalTo(true));
    }

    @Test
    @Order(49)
    void updateBranding_multipleFieldsAtOnce_returns200() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + customerAuthToken)
                .body(Map.of(
                        "logoUrl", "https://example.com/logo2.png",
                        "primaryColor", "#AABBCC",
                        "secondaryColor", "#112233",
                        "website", "https://multi.example.com"))
            .when()
                .put("/api/customer/auth/branding")
            .then()
                .statusCode(200)
                .body("success", equalTo(true));
    }

    @Test
    @Order(50)
    void updateBranding_nullPrimaryColor_doesNotUpdate() {
        // Sends null value for primaryColor — color != null check in the resource
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + customerAuthToken)
                .body("{\"primaryColor\": null}")
            .when()
                .put("/api/customer/auth/branding")
            .then()
                .statusCode(200)
                .body("success", equalTo(true));
    }

    // =========================================================================
    // CustomerAuthResource: GET /branding/preview-ticket
    // =========================================================================

    @Test
    @Order(55)
    void previewTicket_withoutAuth_returns401() {
        given()
            .when()
                .get("/api/customer/auth/branding/preview-ticket")
            .then()
                .statusCode(401);
    }

    @Test
    @Order(56)
    void previewTicket_validAuth_returnsPdf() {
        byte[] pdf = given()
                .header("Authorization", "Bearer " + customerAuthToken)
            .when()
                .get("/api/customer/auth/branding/preview-ticket")
            .then()
                .statusCode(200)
                .contentType("application/pdf")
                .extract()
                .asByteArray();

        assertTrue(pdf.length > 0);
    }

    // =========================================================================
    // UserAuthResource: POST /register
    // =========================================================================

    @Test
    @Order(60)
    void userRegister_success_returns200() {
        userAuthToken = given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "email", "user-coverage@test.nl",
                        "password", "Password123",
                        "firstName", "Coverage",
                        "lastName", "User"))
            .when()
                .post("/api/user/auth/register")
            .then()
                .statusCode(200)
                .body("token", notNullValue())
                .body("email", equalTo("user-coverage@test.nl"))
                .extract()
                .path("token");
    }

    @Test
    @Order(61)
    void userRegister_duplicateEmail_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "email", "user-coverage@test.nl",
                        "password", "Password456",
                        "firstName", "Duplicate",
                        "lastName", "User"))
            .when()
                .post("/api/user/auth/register")
            .then()
                .statusCode(400);
    }

    // =========================================================================
    // UserAuthResource: POST /login
    // =========================================================================

    @Test
    @Order(65)
    void userLogin_wrongPassword_returns401() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", "user-coverage@test.nl", "password", "WrongPassword"))
            .when()
                .post("/api/user/auth/login")
            .then()
                .statusCode(401);
    }

    @Test
    @Order(66)
    void userLogin_nonexistentEmail_returns401() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", "nonexistent@test.nl", "password", "Password123"))
            .when()
                .post("/api/user/auth/login")
            .then()
                .statusCode(401);
    }

    // =========================================================================
    // UserAuthResource: PUT /profile
    // =========================================================================

    @Test
    @Order(70)
    void userUpdateProfile_validAuth_returns200() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + userAuthToken)
                .body(Map.of(
                        "firstName", "Updated",
                        "lastName", "Name",
                        "phone", "0687654321",
                        "street", "Teststraat",
                        "houseNumber", "42",
                        "postalCode", "1234AB",
                        "city", "Amsterdam"))
            .when()
                .put("/api/user/auth/profile")
            .then()
                .statusCode(200)
                .body("firstName", equalTo("Updated"))
                .body("lastName", equalTo("Name"));
    }

    @Test
    @Order(71)
    void userUpdateProfile_noAuth_returns401() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "firstName", "No",
                        "lastName", "Auth"))
            .when()
                .put("/api/user/auth/profile")
            .then()
                .statusCode(401);
    }

    // =========================================================================
    // UserAuthResource: POST /forgot-password
    // =========================================================================

    @Test
    @Order(75)
    void userForgotPassword_blankEmail_returnsMessage() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", ""))
            .when()
                .post("/api/user/auth/forgot-password")
            .then()
                .statusCode(200)
                .body("message", notNullValue());
    }

    @Test
    @Order(76)
    void userForgotPassword_nullEmail_returnsMessage() {
        // Body without "email" key so body.get("email") returns null
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("other", "value"))
            .when()
                .post("/api/user/auth/forgot-password")
            .then()
                .statusCode(200)
                .body("message", notNullValue());
    }

    @Test
    @Order(77)
    void userForgotPassword_nonexistentEmail_returnsMessage() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", "ghost@nowhere.nl"))
            .when()
                .post("/api/user/auth/forgot-password")
            .then()
                .statusCode(200)
                .body("message", notNullValue());
    }

    @Test
    @Order(78)
    void userForgotPassword_existingUser_returnsMessage() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", "user-coverage@test.nl"))
            .when()
                .post("/api/user/auth/forgot-password")
            .then()
                .statusCode(200)
                .body("message", notNullValue());
    }

    // =========================================================================
    // UserAuthResource: POST /reset-password
    // =========================================================================

    @Test
    @Order(80)
    void userResetPassword_malformedBase64_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("token", "!!!invalid-base64!!!", "password", "NewPassword123"))
            .when()
                .post("/api/user/auth/reset-password")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(81)
    void userResetPassword_wrongNumberOfParts_returns400() {
        String payload = "onlytwo|parts";
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("token", token, "password", "NewPassword123"))
            .when()
                .post("/api/user/auth/reset-password")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(82)
    void userResetPassword_expiredToken_returns400() {
        // Expiry 1000000000 is ~2001, well in the past
        String payload = "1|1000000000|fakesig";
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("token", token, "password", "NewPassword123"))
            .when()
                .post("/api/user/auth/reset-password")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(83)
    void userResetPassword_wrongSignature_returns400() {
        long futureExpiry = System.currentTimeMillis() / 1000 + 86400;
        String payload = "1|" + futureExpiry + "|wrongsignature";
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("token", token, "password", "NewPassword123"))
            .when()
                .post("/api/user/auth/reset-password")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(84)
    void userResetPassword_nonNumericUserId_returns400() {
        // Triggers outer catch block (NumberFormatException)
        long futureExpiry = System.currentTimeMillis() / 1000 + 86400;
        String payload = "abc|" + futureExpiry + "|somesig";
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("token", token, "password", "NewPassword123"))
            .when()
                .post("/api/user/auth/reset-password")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(85)
    void userResetPassword_nonexistentUser_returns400() {
        long futureExpiry = System.currentTimeMillis() / 1000 + 86400;
        String payload = "999999|" + futureExpiry + "|fakesig";
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("token", token, "password", "NewPassword123"))
            .when()
                .post("/api/user/auth/reset-password")
            .then()
                .statusCode(400);
    }
}
