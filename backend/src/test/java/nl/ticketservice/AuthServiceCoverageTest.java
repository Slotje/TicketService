package nl.ticketservice;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
public class AuthServiceCoverageTest {

    private static String adminToken;
    private static Long createdScannerId;

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

    // ---------------------------------------------------------------
    // Admin Auth: validateToken edge cases via /api/admin/auth/verify
    // ---------------------------------------------------------------

    @Test
    @Order(1)
    void adminVerify_malformedBase64Token_returns401() {
        // Send a token that is valid base64 but has wrong number of pipe-delimited parts (only 2)
        String malformedPayload = "somedata|otherdata";
        String malformedToken = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(malformedPayload.getBytes(StandardCharsets.UTF_8));

        given()
                .header("Authorization", "Bearer " + malformedToken)
                .when()
                .get("/api/admin/auth/verify")
                .then()
                .statusCode(401);
    }

    @Test
    @Order(2)
    void adminVerify_expiredToken_returns401() {
        // Create a token with an expiry in the past: id|expiry|signature
        // The signature will be wrong but expiry check happens first
        String expiredPayload = "1|1000000000|fakesignature";
        String expiredToken = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(expiredPayload.getBytes(StandardCharsets.UTF_8));

        given()
                .header("Authorization", "Bearer " + expiredToken)
                .when()
                .get("/api/admin/auth/verify")
                .then()
                .statusCode(401);
    }

    @Test
    @Order(3)
    void adminVerify_wrongSignatureToken_returns401() {
        // Create a token with future expiry but wrong HMAC signature
        long futureExpiry = System.currentTimeMillis() / 1000 + 86400;
        String payload = "1|" + futureExpiry + "|deadbeefdeadbeefdeadbeef";
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/admin/auth/verify")
                .then()
                .statusCode(401);
    }

    @Test
    @Order(4)
    void adminVerify_nonBase64Token_returns401() {
        // Token that is not valid base64 at all - will trigger exception path
        given()
                .header("Authorization", "Bearer !!!not-valid-base64!!!")
                .when()
                .get("/api/admin/auth/verify")
                .then()
                .statusCode(401);
    }

    @Test
    @Order(5)
    void adminVerify_missingBearerPrefix_returns401() {
        // Authorization header without "Bearer " prefix
        given()
                .header("Authorization", "Basic sometoken")
                .when()
                .get("/api/admin/auth/verify")
                .then()
                .statusCode(401);
    }

    @Test
    @Order(6)
    void adminVerify_emptyAuthHeader_returns401() {
        given()
                .header("Authorization", "")
                .when()
                .get("/api/admin/auth/verify")
                .then()
                .statusCode(401);
    }

    // ---------------------------------------------------------------
    // Admin Auth: setup endpoint when admin already exists
    // ---------------------------------------------------------------

    @Test
    @Order(7)
    void adminSetup_whenAdminExists_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", "newadmin@test.nl", "password", "password123", "name", "New Admin"))
                .when()
                .post("/api/admin/auth/setup")
                .then()
                .statusCode(400);
    }

    @Test
    @Order(8)
    void adminSetup_needsSetup_returnsFalse() {
        given()
                .when()
                .get("/api/admin/auth/setup")
                .then()
                .statusCode(200)
                .body("needsSetup", equalTo(false));
    }

    // ---------------------------------------------------------------
    // Scanner Auth (AuthService): validateToken edge cases via /api/auth/verify
    // ---------------------------------------------------------------

    @Test
    @Order(10)
    void scannerVerify_malformedToken_twoPartsOnly_returns401() {
        String malformedPayload = "onlyonepart|secondpart";
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(malformedPayload.getBytes(StandardCharsets.UTF_8));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/auth/verify")
                .then()
                .statusCode(401);
    }

    @Test
    @Order(11)
    void scannerVerify_expiredToken_returns401() {
        String expiredPayload = "1|1000000000|fakesignature";
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(expiredPayload.getBytes(StandardCharsets.UTF_8));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/auth/verify")
                .then()
                .statusCode(401);
    }

    @Test
    @Order(12)
    void scannerVerify_wrongSignature_returns401() {
        long futureExpiry = System.currentTimeMillis() / 1000 + 86400;
        String payload = "1|" + futureExpiry + "|badhmacsignaturevalue";
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/auth/verify")
                .then()
                .statusCode(401);
    }

    @Test
    @Order(13)
    void scannerVerify_nonBase64Token_returns401() {
        given()
                .header("Authorization", "Bearer !!!invalid!!!")
                .when()
                .get("/api/auth/verify")
                .then()
                .statusCode(401);
    }

    @Test
    @Order(14)
    void scannerVerify_missingBearerPrefix_returns401() {
        given()
                .header("Authorization", "Token something")
                .when()
                .get("/api/auth/verify")
                .then()
                .statusCode(401);
    }

    @Test
    @Order(15)
    void scannerVerify_nonNumericIdInToken_returns401() {
        // Token where the id part is not numeric - triggers NumberFormatException -> catch block
        long futureExpiry = System.currentTimeMillis() / 1000 + 86400;
        String payload = "notanumber|" + futureExpiry + "|somesignature";
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/auth/verify")
                .then()
                .statusCode(401);
    }

    // ---------------------------------------------------------------
    // Scanner user management: toggle/delete nonexistent IDs
    // ---------------------------------------------------------------

    @Test
    @Order(20)
    void toggleUser_nonexistentId_returns404() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .when()
                .patch("/api/auth/users/999999/toggle")
                .then()
                .statusCode(404);
    }

    @Test
    @Order(21)
    void deleteUser_nonexistentId_returns404() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .when()
                .delete("/api/auth/users/999999")
                .then()
                .statusCode(404);
    }

    @Test
    @Order(22)
    void createUser_withoutAdminHeader_returns401() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", "noauth", "password", "pass1234", "displayName", "No Auth"))
                .when()
                .post("/api/auth/users")
                .then()
                .statusCode(401);
    }

    @Test
    @Order(23)
    void getUsers_withInvalidAdminToken_returns401() {
        given()
                .header("Authorization", "Bearer invalidtoken")
                .when()
                .get("/api/auth/users")
                .then()
                .statusCode(401);
    }

    @Test
    @Order(24)
    void createScannerUser_thenToggle_thenLoginFails() {
        // Create a scanner user
        Number rawId = given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(Map.of("username", "coveragescanner", "password", "pass1234", "displayName", "Coverage Scanner"))
                .when()
                .post("/api/auth/users")
                .then()
                .statusCode(200)
                .extract()
                .path("id");

        createdScannerId = rawId.longValue();

        // Toggle to inactive
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .when()
                .patch("/api/auth/users/" + createdScannerId + "/toggle")
                .then()
                .statusCode(200)
                .body("active", equalTo(false));

        // Try to login with inactive scanner - should fail
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", "coveragescanner", "password", "pass1234"))
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(401);
    }

    @Test
    @Order(25)
    void deleteScannerUser_thenCleanup() {
        // Delete the scanner user created in previous test
        if (createdScannerId != null) {
            given()
                    .contentType(ContentType.JSON)
                    .header("Authorization", "Bearer " + getAdminToken())
                    .when()
                    .delete("/api/auth/users/" + createdScannerId)
                    .then()
                    .statusCode(204);
        }
    }

    // ---------------------------------------------------------------
    // User Auth (UserAuthService): validateToken edge cases via /api/user/auth/verify
    // ---------------------------------------------------------------

    @Test
    @Order(30)
    void userVerify_malformedToken_returns401() {
        String malformedPayload = "one|two";
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(malformedPayload.getBytes(StandardCharsets.UTF_8));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/user/auth/verify")
                .then()
                .statusCode(401);
    }

    @Test
    @Order(31)
    void userVerify_expiredToken_returns401() {
        String expiredPayload = "1|1000000000|fakesig";
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(expiredPayload.getBytes(StandardCharsets.UTF_8));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/user/auth/verify")
                .then()
                .statusCode(401);
    }

    @Test
    @Order(32)
    void userVerify_wrongSignature_returns401() {
        long futureExpiry = System.currentTimeMillis() / 1000 + 86400;
        String payload = "1|" + futureExpiry + "|wrongsignaturevalue";
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/user/auth/verify")
                .then()
                .statusCode(401);
    }

    @Test
    @Order(33)
    void userVerify_nonBase64Token_returns401() {
        given()
                .header("Authorization", "Bearer @@@@not-base64@@@@")
                .when()
                .get("/api/user/auth/verify")
                .then()
                .statusCode(401);
    }

    @Test
    @Order(34)
    void userVerify_missingBearerPrefix_returns401() {
        given()
                .header("Authorization", "NotBearer something")
                .when()
                .get("/api/user/auth/verify")
                .then()
                .statusCode(401);
    }

    @Test
    @Order(35)
    void userVerify_emptyToken_returns401() {
        given()
                .header("Authorization", "Bearer ")
                .when()
                .get("/api/user/auth/verify")
                .then()
                .statusCode(401);
    }

    @Test
    @Order(36)
    void userRegister_missingEmail_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("password", "password123", "name", "Test"))
                .when()
                .post("/api/user/auth/register")
                .then()
                .statusCode(400);
    }

    @Test
    @Order(37)
    void userRegister_missingPassword_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", "missing-pw@test.com", "name", "Test"))
                .when()
                .post("/api/user/auth/register")
                .then()
                .statusCode(400);
    }

    @Test
    @Order(38)
    void userRegister_missingName_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", "missing-name@test.com", "password", "password123"))
                .when()
                .post("/api/user/auth/register")
                .then()
                .statusCode(400);
    }

    @Test
    @Order(39)
    void userRegister_invalidEmail_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", "not-an-email", "password", "password123", "name", "Test"))
                .when()
                .post("/api/user/auth/register")
                .then()
                .statusCode(400);
    }

    @Test
    @Order(40)
    void userRegister_shortPassword_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", "shortpw@test.com", "password", "abc", "name", "Test"))
                .when()
                .post("/api/user/auth/register")
                .then()
                .statusCode(400);
    }

    // ---------------------------------------------------------------
    // Customer Auth: validateToken edge cases via /api/customer/auth/verify
    // ---------------------------------------------------------------

    @Test
    @Order(50)
    void customerVerify_malformedToken_returns401() {
        String malformedPayload = "a|b";
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(malformedPayload.getBytes(StandardCharsets.UTF_8));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/customer/auth/verify")
                .then()
                .statusCode(401);
    }

    @Test
    @Order(51)
    void customerVerify_expiredToken_returns401() {
        String expiredPayload = "1|1000000000|fakesig";
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(expiredPayload.getBytes(StandardCharsets.UTF_8));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/customer/auth/verify")
                .then()
                .statusCode(401);
    }

    @Test
    @Order(52)
    void customerVerify_wrongSignature_returns401() {
        long futureExpiry = System.currentTimeMillis() / 1000 + 86400;
        String payload = "1|" + futureExpiry + "|wrongsig";
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/customer/auth/verify")
                .then()
                .statusCode(401);
    }

    @Test
    @Order(53)
    void customerVerify_nonBase64Token_returns401() {
        given()
                .header("Authorization", "Bearer $$invalid$$")
                .when()
                .get("/api/customer/auth/verify")
                .then()
                .statusCode(401);
    }

    @Test
    @Order(54)
    void customerVerify_missingBearerPrefix_returns401() {
        given()
                .header("Authorization", "Token something")
                .when()
                .get("/api/customer/auth/verify")
                .then()
                .statusCode(401);
    }

    @Test
    @Order(55)
    void customerVerify_nonNumericIdInToken_returns401() {
        long futureExpiry = System.currentTimeMillis() / 1000 + 86400;
        String payload = "abc|" + futureExpiry + "|somesig";
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/customer/auth/verify")
                .then()
                .statusCode(401);
    }

    @Test
    @Order(56)
    void customerLogin_withNonexistentEmail_returns401() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", "doesnotexist@nowhere.com", "password", "whatever"))
                .when()
                .post("/api/customer/auth/login")
                .then()
                .statusCode(401);
    }

    @Test
    @Order(57)
    void customerSetPassword_withInvalidInviteToken_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("token", "nonexistent-invite-token", "password", "newpassword123"))
                .when()
                .post("/api/customer/auth/set-password")
                .then()
                .statusCode(400);
    }

    @Test
    @Order(58)
    void customerInvite_withInvalidToken_returns400() {
        given()
                .when()
                .get("/api/customer/auth/invite/nonexistent-token-xyz")
                .then()
                .statusCode(400);
    }

    // ---------------------------------------------------------------
    // Admin login edge cases
    // ---------------------------------------------------------------

    @Test
    @Order(60)
    void adminLogin_emptyBody_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/admin/auth/login")
                .then()
                .statusCode(400);
    }

    @Test
    @Order(61)
    void scannerLogin_emptyBody_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(400);
    }

    @Test
    @Order(62)
    void userLogin_emptyBody_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/user/auth/login")
                .then()
                .statusCode(400);
    }

    // ---------------------------------------------------------------
    // Token with single part (parts.length != 3 path)
    // ---------------------------------------------------------------

    @Test
    @Order(70)
    void adminVerify_singlePartToken_returns401() {
        String singlePart = "justonepart";
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(singlePart.getBytes(StandardCharsets.UTF_8));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/admin/auth/verify")
                .then()
                .statusCode(401);
    }

    @Test
    @Order(71)
    void scannerVerify_fourPartsToken_returns401() {
        String fourParts = "a|b|c|d";
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(fourParts.getBytes(StandardCharsets.UTF_8));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/auth/verify")
                .then()
                .statusCode(401);
    }

    @Test
    @Order(72)
    void userVerify_fourPartsToken_returns401() {
        String fourParts = "a|b|c|d";
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(fourParts.getBytes(StandardCharsets.UTF_8));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/user/auth/verify")
                .then()
                .statusCode(401);
    }

    @Test
    @Order(73)
    void customerVerify_fourPartsToken_returns401() {
        String fourParts = "a|b|c|d";
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(fourParts.getBytes(StandardCharsets.UTF_8));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/customer/auth/verify")
                .then()
                .statusCode(401);
    }

    // ---------------------------------------------------------------
    // Scanner user management without auth
    // ---------------------------------------------------------------

    @Test
    @Order(80)
    void getUsers_withoutAuth_returns401() {
        given()
                .when()
                .get("/api/auth/users")
                .then()
                .statusCode(401);
    }

    @Test
    @Order(81)
    void toggleUser_withoutAuth_returns401() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .patch("/api/auth/users/1/toggle")
                .then()
                .statusCode(401);
    }

    @Test
    @Order(82)
    void deleteUser_withoutAuth_returns401() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .delete("/api/auth/users/1")
                .then()
                .statusCode(401);
    }

    // ---------------------------------------------------------------
    // Token with non-numeric expiry (triggers exception path)
    // ---------------------------------------------------------------

    @Test
    @Order(90)
    void adminVerify_nonNumericExpiry_returns401() {
        String payload = "1|notanumber|somesig";
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/admin/auth/verify")
                .then()
                .statusCode(401);
    }

    @Test
    @Order(91)
    void scannerVerify_nonNumericExpiry_returns401() {
        String payload = "1|notanumber|somesig";
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/auth/verify")
                .then()
                .statusCode(401);
    }

    @Test
    @Order(92)
    void customerVerify_nonNumericExpiry_returns401() {
        String payload = "1|notanumber|somesig";
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/customer/auth/verify")
                .then()
                .statusCode(401);
    }

    @Test
    @Order(93)
    void userVerify_nonNumericExpiry_returns401() {
        String payload = "1|notanumber|somesig";
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/user/auth/verify")
                .then()
                .statusCode(401);
    }
}
