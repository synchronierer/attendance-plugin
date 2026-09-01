package de.igslandstuhl.database.attendance;
import com.google.zxing.BarcodeFormat;import com.google.zxing.qrcode.QRCodeWriter;import de.igslandstuhl.database.api.*;import de.igslandstuhl.database.server.webserver.access.AccessLevel;import de.igslandstuhl.database.server.webserver.handlers.HttpHandler;import de.igslandstuhl.database.server.webserver.responses.PostResponse;import java.time.*;import java.util.*;import org.slf4j.Logger;import org.slf4j.LoggerFactory;
final class AttendanceRequests {
 private static final Logger LOGGER=LoggerFactory.getLogger(AttendanceRequests.class);private static final Clock CLOCK=Clock.systemUTC();private static final ZoneId ZONE=ZoneId.of("Europe/Berlin");private AttendanceRequests(){}
 static Student strictStudent(User u){return u instanceof Student s&&s.isStudent()?s:null;}static boolean staff(User u){return u!=null&&(u.isTeacher()||u.isAdmin());}
 static void register(AttendancePluginConfig config){
  AttendanceSignageSecurity signage=AttendanceSignageSecurity.fromEnvironment();
  HttpHandler.registerPostRequestHandler("/attendance-checkin",AccessLevel.PUBLIC,r->{
   Student s=strictStudent(r.getUser());
   try {
    String token=r.getString("token");
    long lid=new RotatingQrTokenService(AttendanceRepository.qrSecret(),CLOCK).validate(token);
    if(s==null){
     if(r.getUser()!=null&&r.getUser()!=User.ANONYMOUS){LOGGER.info("CHECKIN_ERROR role_not_student");return PostResponse.forbidden("Dieser Check-in ist nur für Schülerinnen und Schüler.",r);}
     String target="/attendance-checkin?token="+java.net.URLEncoder.encode(token,java.nio.charset.StandardCharsets.UTF_8);
     String login="/login?next="+java.net.URLEncoder.encode(target,java.nio.charset.StandardCharsets.UTF_8);
     LOGGER.info("CHECKIN_LOGIN_REQUIRED");
     return PostResponse.json(Map.of("ok",false,"loginRequired",true,"loginUrl",login),r);
    }
    Map<String,Object> result=AttendanceRepository.qrCheckIn(s.getId(),lid,CLOCK.instant());
    String status=String.valueOf(result.get("status"));
    if("ALREADY_ARRIVED".equals(status))LOGGER.info("CHECKIN_ALREADY_ARRIVED studentId={}",s.getId());
    else LOGGER.info("CHECKIN_RECORDED studentId={}",s.getId());
    return PostResponse.json(Map.of("ok",true,"checkin",result),r);
   } catch(IllegalArgumentException e){
    if(e.getMessage()!=null&&e.getMessage().contains("nicht mehr gültig"))LOGGER.info("CHECKIN_TOKEN_EXPIRED");
    else LOGGER.warn("CHECKIN_ERROR {}",e.getMessage());
    return PostResponse.badRequest(e.getMessage(),r);
   } catch(Exception e){LOGGER.warn("CHECKIN_ERROR {}",e.getClass().getSimpleName());return PostResponse.internalServerError("Check-in konnte nicht verarbeitet werden.",r);}
  });
  HttpHandler.registerPostRequestHandler("/attendance-display-token",AccessLevel.PUBLIC,r->{
   if(!signage.allow(r.getIP(),CLOCK.instant()))return PostResponse.tooManyRequests("Zu viele Signage-Anfragen",r);
   if(!signage.authenticated(r.getCookies()))return PostResponse.unauthorized("Ungültige Signage-Autorisierung",r);
   try{Map<String,Object>l=AttendanceRepository.locationByCode(r.getString("location"));if(!(Boolean)l.get("active"))return PostResponse.notFound("Standort nicht gefunden",r);String token=new RotatingQrTokenService(AttendanceRepository.qrSecret(),CLOCK).create(((Number)l.get("id")).longValue());String path="/attendance-checkin?token="+java.net.URLEncoder.encode(token,java.nio.charset.StandardCharsets.UTF_8);return PostResponse.json(Map.of("location",l,"checkinUrl",path,"qrSvg",qrSvg(signage.publicBaseUrl()+path),"validForSeconds",30),r);}catch(IllegalArgumentException e){return PostResponse.notFound(e.getMessage(),r);}
  });
  HttpHandler.registerPostRequestHandler("/attendance-signage-token",AccessLevel.PUBLIC,r->{
   if(!signage.allow(r.getIP(),CLOCK.instant()))return PostResponse.tooManyRequests("Zu viele Signage-Anfragen",r);
   if(!signage.authenticated(r.getCookies()))return PostResponse.unauthorized("Ungültige Signage-Autorisierung",r);
   try{
    Map<String,Object>l=AttendanceRepository.locationByCode(r.getString("location"));
    if(!(Boolean)l.get("active"))return PostResponse.notFound("Standort nicht gefunden",r);
    Instant now=CLOCK.instant();long validUntilEpoch=((now.getEpochSecond()/RotatingQrTokenService.WINDOW_SECONDS)+1)*RotatingQrTokenService.WINDOW_SECONDS;
    String token=new RotatingQrTokenService(AttendanceRepository.qrSecret(),CLOCK).create(((Number)l.get("id")).longValue());
    String path="/attendance-checkin?token="+java.net.URLEncoder.encode(token,java.nio.charset.StandardCharsets.UTF_8);
    Map<String,Object>location=Map.of("code",l.get("code"),"name",l.get("name"),"type",l.get("type"));
    return PostResponse.json(Map.of("location",location,"checkinPath",path,"qrSvg",qrSvg(signage.publicBaseUrl()+path),"validUntil",Instant.ofEpochSecond(validUntilEpoch).toString(),"validForSeconds",Math.max(0,validUntilEpoch-now.getEpochSecond())),r);
   }catch(IllegalArgumentException e){return PostResponse.notFound(e.getMessage(),r);}
  });
  HttpHandler.registerPostRequestHandler("/attendance-data",AccessLevel.TEACHER,r->{if(!staff(r.getUser()))return PostResponse.forbidden("Nur Lehrkräfte und Admins",r);try{LocalDate date=LocalDate.parse(r.getString("date"));int lesson=r.getInt("lesson");LessonSchedule schedule=new LessonSchedule(config.lessonTimes());Instant cutoff=schedule.cutoff(date,lesson,ZONE,CLOCK.instant());Map<String,Object>out=new LinkedHashMap<>();out.put("date",date.toString());out.put("lesson",lesson);out.put("cutoff",cutoff.toString());out.put("lessonCheckTimes",schedule.values());out.put("locations",AttendanceRepository.locations(true));out.put("students",AttendanceRepository.snapshot(date,cutoff));out.put("preferences",AttendanceRepository.preferences(r.getUser().getUsername()));return PostResponse.json(out,r);}catch(IllegalArgumentException e){return PostResponse.badRequest(e.getMessage(),r);}});
  HttpHandler.registerPostRequestHandler("/attendance-manual-arrival",AccessLevel.TEACHER,r->{User u=r.getUser();if(!staff(u))return PostResponse.forbidden("Schüler dürfen keine manuelle Anwesenheit setzen",r);List<Map<String,Object>>results=new ArrayList<>();for(Object value:r.getList("studentIds")){if(!(value instanceof Number n)||Student.get(n.intValue())==null)return PostResponse.badRequest("Ungültige Schülerauswahl",r);results.add(AttendanceRepository.manualArrival(n.intValue(),principal(u),CLOCK.instant()));}return PostResponse.json(Map.of("ok",true,"results",results),r);});
  HttpHandler.registerPostRequestHandler("/attendance-preferences-save",AccessLevel.TEACHER,r->{User u=r.getUser();if(!staff(u))return PostResponse.forbidden("Nur Lehrkräfte und Admins",r);AttendanceRepository.savePreferences(u.getUsername(),r.getString("location"),r.getList("classIds"));return PostResponse.json(Map.of("ok",true),r);});
  HttpHandler.registerPostRequestHandler("/attendance-admin-data",AccessLevel.ADMIN,r->PostResponse.json(Map.of("locations",AttendanceRepository.locations(false)),r));
  HttpHandler.registerPostRequestHandler("/attendance-location-save",AccessLevel.ADMIN,r->{try{Long id=r.containsKey("id")?Long.valueOf(r.getInt("id")):null;Map<String,Object>location=AttendanceRepository.upsertLocation(id,r.getString("code"),r.getString("name"),r.getString("type"),r.getBoolean("active"),r.getInt("displayOrder"));LOGGER.info("Attendance location saved: id={}, code={}, type={}, active={}",location.get("id"),location.get("code"),location.get("type"),location.get("active"));return PostResponse.json(location,r);}catch(IllegalArgumentException e){return PostResponse.badRequest(e.getMessage(),r);}});
 }
 private static String principal(User u){if(u instanceof Teacher t)return "TEACHER:"+t.getId();return "ADMIN:"+u.getUsername();}
 private static String qrSvg(String value)throws Exception{var hints=new java.util.EnumMap<com.google.zxing.EncodeHintType,Object>(com.google.zxing.EncodeHintType.class);hints.put(com.google.zxing.EncodeHintType.ERROR_CORRECTION,com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M);hints.put(com.google.zxing.EncodeHintType.MARGIN,4);var m=new QRCodeWriter().encode(value,BarcodeFormat.QR_CODE,0,0,hints);int width=m.getWidth(),height=m.getHeight();StringBuilder s=new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ").append(width).append(' ').append(height).append("\" shape-rendering=\"crispEdges\"><rect width=\"").append(width).append("\" height=\"").append(height).append("\" fill=\"white\"/><path fill=\"black\" d=\"");for(int y=0;y<height;y++)for(int x=0;x<width;x++)if(m.get(x,y))s.append('M').append(x).append(' ').append(y).append("h1v1h-1z");return s.append("\"/></svg>").toString();}
}
