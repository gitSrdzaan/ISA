package com.example.demo.security;

import com.example.demo.model.*;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;

// Utility klasa za rad sa JSON Web Tokenima
@Component
public class TokenUtils {

    // Izdavac tokena
    @Value("spring-security-example")
    private String APP_NAME;

    // Tajna koju samo backend aplikacija treba da zna kako bi mogla da generise i proveri JWT https://jwt.io/
    @Value("cimiji")
    public String SECRET;

    // Period vazenja
    @Value("300000")
    private int EXPIRES_IN;

    // Naziv headera kroz koji ce se prosledjivati JWT u komunikaciji server-klijent
    @Value("Authorization")
    private String AUTH_HEADER;

    // Moguce je generisati JWT za razlicite klijente (npr. web i mobilni klijenti nece imati isto trajanje JWT, JWT za mobilne klijente ce trajati duze jer se mozda aplikacija redje koristi na taj nacin)
    private static final String AUDIENCE_UNKNOWN = "unknown";
    private static final String AUDIENCE_WEB = "web";
    private static final String AUDIENCE_MOBILE = "mobile";
    private static final String AUDIENCE_TABLET = "tablet";

    // Algoritam za potpisivanje JWT
    private SignatureAlgorithm SIGNATURE_ALGORITHM = SignatureAlgorithm.HS512;

    @Autowired
    TimeProvider timeProvider;

    public String generateToken(Pacijent pacijent, Authority role) {

        return Jwts.builder()
                .setIssuer(APP_NAME)
                .setSubject(pacijent.getUsername())
                .setAudience(generateAudience())
                .setIssuedAt(timeProvider.now())
                .setExpiration(generateExpirationDate())
                .claim("role", role)
                .claim("id", pacijent.getId())
                .signWith(SIGNATURE_ALGORITHM, SECRET).compact();
    }

    public String generateToken(Doktor doktor, Authority role) {

        return Jwts.builder()
                .setIssuer(APP_NAME)
                .setSubject(doktor.getUsername())
                .setAudience(generateAudience())
                .setIssuedAt(timeProvider.now())
                .setExpiration(generateExpirationDate())
                .claim("role", role)
                .claim("id", doktor.getId())
        //        .claim("promenjenaLozinka", doktor.getPromenjenaLozinka())
                .signWith(SIGNATURE_ALGORITHM, SECRET).compact();
    }

    public String generateToken(MedicinskaSestra medicninskaSestra, Authority role) {

        return Jwts.builder()
                .setIssuer(APP_NAME)
                .setSubject(medicninskaSestra.getUsername())
                .setAudience(generateAudience())
                .setIssuedAt(timeProvider.now())
                .setExpiration(generateExpirationDate())
                .claim("role", role)
                .claim("id", medicninskaSestra.getId())
               // .claim("promenjenaLozinka", medicninskaSestra.getPromenjenaLozinka())
                .signWith(SIGNATURE_ALGORITHM, SECRET).compact();
    }

    public String generateToken(AdministratorKlinike adminKlinike, Authority role) {

        return Jwts.builder()
                .setIssuer(APP_NAME)
                .setSubject(adminKlinike.getUsername())
                .setAudience(generateAudience())
                .setIssuedAt(timeProvider.now())
                .setExpiration(generateExpirationDate())
                .claim("role", role)
                .claim("id", adminKlinike.getId())
              //  .claim("promenjenaLozinka", adminKlinike.getPromenjenaLozinka())
                .signWith(SIGNATURE_ALGORITHM, SECRET).compact();
    }

    public String generateToken(AdministratorKlinickogCentra adminCentra, Authority role) {

        return Jwts.builder()
                .setIssuer(APP_NAME)
                .setSubject(adminCentra.getUsername())
                .setAudience(generateAudience())
                .setIssuedAt(timeProvider.now())
                .setExpiration(generateExpirationDate())
                .claim("role", role)
                .claim("id", adminCentra.getId())
              //  .claim("promenjenaLozinka", adminCentra.getPromenjenaLozinka())
               .signWith(SIGNATURE_ALGORITHM, SECRET).compact();
    }
    private String generateAudience() {
        return AUDIENCE_WEB;
    }

    private Date generateExpirationDate() {
        return new Date(timeProvider.now().getTime() + EXPIRES_IN);
    }

    // Funkcija za refresh JWT tokena
    public String refreshToken(String token) {
        String refreshedToken;
        try {
            final Claims claims = this.getAllClaimsFromToken(token);
            claims.setIssuedAt(timeProvider.now());
            refreshedToken = Jwts.builder()
                    .setClaims(claims)
                    .setExpiration(generateExpirationDate())
                    .signWith(SIGNATURE_ALGORITHM, SECRET).compact();
        } catch (Exception e) {
            refreshedToken = null;
        }
        return refreshedToken;
    }

    public boolean canTokenBeRefreshed(String token, Date lastPasswordReset) {
        final Date created = this.getIssuedAtDateFromToken(token);
        return (!(this.isCreatedBeforeLastPasswordReset(created, lastPasswordReset))
                && (!(this.isTokenExpired(token)) || this.ignoreTokenExpiration(token)));
    }

    // Funkcija za validaciju JWT tokena
    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = getUsernameFromToken(token);
        return (username != null && username.equals(userDetails.getUsername()));

    }


    public String getUsernameFromToken(String token) {
        String username;
        try {
            final Claims claims = this.getAllClaimsFromToken(token);
            username = claims.getSubject();
        } catch (Exception e) {
            username = null;
        }
        return username;
    }


    public Date getIssuedAtDateFromToken(String token) {
        Date issueAt;
        try {
            final Claims claims = this.getAllClaimsFromToken(token);
            issueAt = claims.getIssuedAt();
        } catch (Exception e) {
            issueAt = null;
        }
        return issueAt;
    }

    public String getAudienceFromToken(String token) {
        String audience;
        try {
            final Claims claims = this.getAllClaimsFromToken(token);
            audience = claims.getAudience();
        } catch (Exception e) {
            audience = null;
        }
        return audience;
    }

    public Date getExpirationDateFromToken(String token) {
        Date expiration;
        try {
            final Claims claims = this.getAllClaimsFromToken(token);
            expiration = claims.getExpiration();
        } catch (Exception e) {
            expiration = null;
        }
        return expiration;
    }

    public int getExpiredIn() {
        return EXPIRES_IN;
    }

    // Funkcija za preuzimanje JWT tokena iz zahteva
    public String getToken(HttpServletRequest request) {
        String authHeader = getAuthHeaderFromHeader(request);

        // JWT se prosledjuje kroz header Authorization u formatu:
        // Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        return null;
    }

    public String getAuthHeaderFromHeader(HttpServletRequest request) {
        return request.getHeader(AUTH_HEADER);
    }

    private Boolean isCreatedBeforeLastPasswordReset(Date created, Date lastPasswordReset) {
        return (lastPasswordReset != null && created.before(lastPasswordReset));
    }

    private Boolean isTokenExpired(String token) {
        final Date expiration = this.getExpirationDateFromToken(token);
        return expiration.before(new Date());
    }

    private Boolean ignoreTokenExpiration(String token) {
        String audience = this.getAudienceFromToken(token);
        return (audience.equals(AUDIENCE_TABLET) || audience.equals(AUDIENCE_MOBILE));
    }

    // Funkcija za citanje svih podataka iz JWT tokena
    private Claims getAllClaimsFromToken(String token) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .setSigningKey(SECRET)
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            claims = null;
        }
        return claims;
    }

}