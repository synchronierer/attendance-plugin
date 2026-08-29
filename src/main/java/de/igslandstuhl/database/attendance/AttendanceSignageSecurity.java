package de.igslandstuhl.database.attendance;

import de.igslandstuhl.database.server.webserver.Cookie;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class AttendanceSignageSecurity {
 static final String COOKIE_NAME="attendance_signage_service_key";
 private final byte[] serviceKey;
 private final String publicBaseUrl;
 private final Map<String,Window> limits=new ConcurrentHashMap<>();
 private final int requestsPerMinute;

 AttendanceSignageSecurity(String serviceKey,String publicBaseUrl){this(serviceKey,publicBaseUrl,120);}
 AttendanceSignageSecurity(String serviceKey,String publicBaseUrl,int requestsPerMinute){
  this.serviceKey=decodeKey(serviceKey);
  this.publicBaseUrl=validateBaseUrl(publicBaseUrl);
  if(requestsPerMinute<1||requestsPerMinute>10000)throw new IllegalArgumentException("Ungültiges Signage-Rate-Limit");
  this.requestsPerMinute=requestsPerMinute;
 }
 static AttendanceSignageSecurity fromEnvironment(){return new AttendanceSignageSecurity(System.getenv("ATTENDANCE_SIGNAGE_SERVICE_KEY"),System.getenv("ATTENDANCE_PUBLIC_BASE_URL"));}
 boolean authenticated(Cookie[] cookies){
  byte[] supplied=new byte[0];
  if(cookies!=null)for(Cookie cookie:cookies)if(COOKIE_NAME.equals(cookie.getName())){supplied=decodeSupplied(cookie.getValue());break;}
  return MessageDigest.isEqual(serviceKey,supplied);
 }
 boolean allow(String ip,Instant now){
  String key=ip==null?"unknown":ip;
  long minute=now.getEpochSecond()/60;
  Window updated=limits.compute(key,(ignored,current)->current==null||current.minute()!=minute?new Window(minute,1):new Window(minute,current.count()+1));
  if(limits.size()>1024)limits.entrySet().removeIf(entry->entry.getValue().minute()<minute-2);
  return updated.count()<=requestsPerMinute;
 }
 String publicBaseUrl(){return publicBaseUrl;}
 private static byte[] decodeKey(String value){
  if(value==null||!value.matches("[A-Za-z0-9_-]{43,128}"))throw new IllegalStateException("ATTENDANCE_SIGNAGE_SERVICE_KEY fehlt oder ist unsicher");
  try{byte[] decoded=Base64.getUrlDecoder().decode(value);if(decoded.length<32)throw new IllegalStateException("ATTENDANCE_SIGNAGE_SERVICE_KEY ist zu kurz");return decoded;}catch(IllegalArgumentException e){throw new IllegalStateException("ATTENDANCE_SIGNAGE_SERVICE_KEY ist ungültig");}
 }
 private static byte[] decodeSupplied(String value){try{return value==null?new byte[0]:Base64.getUrlDecoder().decode(value);}catch(IllegalArgumentException e){return new byte[0];}}
 private static String validateBaseUrl(String value){
  try{URI uri=URI.create(value);if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!(uri.getPath()==null||uri.getPath().isEmpty()||"/".equals(uri.getPath())))throw new IllegalArgumentException();return "https://"+uri.getAuthority();}
  catch(RuntimeException e){throw new IllegalStateException("ATTENDANCE_PUBLIC_BASE_URL muss eine vertrauenswürdige HTTPS-Origin sein");}
 }
 private record Window(long minute,int count){}
}
