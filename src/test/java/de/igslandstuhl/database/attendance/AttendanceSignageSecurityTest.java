package de.igslandstuhl.database.attendance;

import static org.junit.jupiter.api.Assertions.*;
import de.igslandstuhl.database.server.webserver.Cookie;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AttendanceSignageSecurityTest {
 private static final String KEY="MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY";
 @Test void acceptsOnlyTheDedicatedCookieWithTheConfiguredKey(){
  AttendanceSignageSecurity security=new AttendanceSignageSecurity(KEY,"https://arcanum-demo.dynv6.net");
  assertTrue(security.authenticated(new Cookie[]{new Cookie(AttendanceSignageSecurity.COOKIE_NAME,KEY)}));
  assertFalse(security.authenticated(new Cookie[0]));
  assertFalse(security.authenticated(new Cookie[]{new Cookie(AttendanceSignageSecurity.COOKIE_NAME,"MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWc")}));
  assertFalse(security.authenticated(new Cookie[]{new Cookie("session",KEY)}));
 }
 @Test void requiresA256BitUrlSafeKeyAndAnHttpsOrigin(){
  assertThrows(IllegalStateException.class,()->new AttendanceSignageSecurity("short","https://arcanum-demo.dynv6.net"));
  assertThrows(IllegalStateException.class,()->new AttendanceSignageSecurity(KEY,"http://arcanum-demo.dynv6.net"));
  assertThrows(IllegalStateException.class,()->new AttendanceSignageSecurity(KEY,"https://arcanum-demo.dynv6.net/path"));
 }
 @Test void rateLimitIsPerAddressAndMinute(){
  AttendanceSignageSecurity security=new AttendanceSignageSecurity(KEY,"https://arcanum-demo.dynv6.net",2);
  Instant now=Instant.parse("2026-08-28T12:00:01Z");
  assertTrue(security.allow("127.0.0.1",now));assertTrue(security.allow("127.0.0.1",now));assertFalse(security.allow("127.0.0.1",now));
  assertTrue(security.allow("127.0.0.2",now));assertTrue(security.allow("127.0.0.1",now.plusSeconds(60)));
 }
}
