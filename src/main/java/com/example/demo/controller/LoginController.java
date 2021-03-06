package com.example.demo.controller;


import java.util.Collection;
import javax.servlet.http.HttpServletResponse;
import javax.validation.ValidationException;

import java.util.HashMap;
import java.util.Map;

import com.example.demo.dto.PacijentDTO;
import com.example.demo.dto.TokenStateDTO;
import com.example.demo.dto.ZahtevZaAutentikacijuDTO;
import com.example.demo.model.*;
import com.example.demo.security.TokenUtils;
import com.example.demo.service.CustomUserDetailsService;
import com.example.demo.service.PacijentService;
import com.example.demo.service.ZahtevZaRegistracijuService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

//Kontroler u kome se nalze putanje kojima mogu pristuiti svi korisnici!!!
//Kontroler zaduzen za autentifikaciju korisnika

@CrossOrigin
@RestController
@RequestMapping(value = "/auth", produces = MediaType.APPLICATION_JSON_VALUE)
public class LoginController {

    @Autowired
    private TokenUtils tokenUtils;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private PacijentService pacijentService;

    @Autowired
    private ZahtevZaRegistracijuService zahtevZaRegistracijuService;

    // Prvi endpoint koji pogadja korisnik kada se loguje.
    // Tada zna samo svoje korisnicko ime i lozinku i to prosledjuje na backend.
    @PostMapping("/login")
    public ResponseEntity createAuthenticationToken(@RequestBody ZahtevZaAutentikacijuDTO authenticationRequest,
                                                                    HttpServletResponse response) {
        Authentication authentication = null;
        try {
            authentication = authenticationManager
                    .authenticate(new UsernamePasswordAuthenticationToken(authenticationRequest.getEmail(),
                            authenticationRequest.getLozinka()));
        } catch (Exception e){
            return new ResponseEntity<>("Uneli ste pograsne podatke", HttpStatus.BAD_REQUEST);
        }


        // Ubaci korisnika u trenutni security kontekst
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Kreiraj token za tog korisnika
        Object user = authentication.getPrincipal();
        String jwt = null;
        int expiresIn = 0;


        if (user instanceof AdministratorKlinickogCentra){
            AdministratorKlinickogCentra administratorKlinickogCentra = (AdministratorKlinickogCentra) user;
            Collection<?> uloge = administratorKlinickogCentra.getAuthorities();

            jwt = tokenUtils.generateToken(administratorKlinickogCentra, (Authority) uloge.iterator().next());
            expiresIn = tokenUtils.getExpiredIn();
        }
        else if (user instanceof AdministratorKlinike){
            AdministratorKlinike administratorKlinike = (AdministratorKlinike) user;
            Collection<?> uloge = administratorKlinike.getAuthorities();
            jwt = tokenUtils.generateToken(administratorKlinike, (Authority) uloge.iterator().next());
            expiresIn = tokenUtils.getExpiredIn();
        }
        else if (user instanceof Doktor){
            Doktor doktor = (Doktor) user;
            Collection<?> uloge = doktor.getAuthorities();
         //   Authority a= (Authority) uloge.iterator().next();
         //   System.out.print( a.getUloga());
            jwt = tokenUtils.generateToken(doktor, (Authority) uloge.iterator().next());
            expiresIn = tokenUtils.getExpiredIn();
        }
        else if (user instanceof MedicinskaSestra){
            MedicinskaSestra medicinskaSestra = (MedicinskaSestra) user;
            Collection<?> uloge = medicinskaSestra.getAuthorities();
            jwt = tokenUtils.generateToken(medicinskaSestra, (Authority) uloge.iterator().next());
            expiresIn = tokenUtils.getExpiredIn();
        }
        else if (user instanceof Pacijent){
            Pacijent pacijent = (Pacijent) user;
            Collection<?> uloge = pacijent.getAuthorities();
            jwt = tokenUtils.generateToken(pacijent, (Authority) uloge.iterator().next());
            //Authority a= (Authority) uloge.iterator().next();
            //System.out.print( a.getUloga());
            expiresIn = tokenUtils.getExpiredIn();
        }
        return ResponseEntity.ok(new TokenStateDTO(jwt, expiresIn));

    }
    @PostMapping("/zahtevZaRegistraciju")
    public ResponseEntity registracija(@RequestBody PacijentDTO pacijentDTO) {

        if (pacijentDTO == null){
            return  new ResponseEntity("Pogresno poslati podaci", HttpStatus.BAD_REQUEST);
        }

        PacijentDTO pacijent = this.pacijentService.izlistajPacijenta(pacijentDTO.getEmail());
        if (pacijent != null) {
            return new ResponseEntity<>("Vec postoji korisnik sa ovim email-om.", HttpStatus.CONFLICT);
        }
        ZahtevZaRegistraciju zahtevZaRegistraciju = new ZahtevZaRegistraciju();
        try {
             zahtevZaRegistraciju  = this.zahtevZaRegistracijuService.posaljiZahtev(pacijentDTO);
        } catch (ValidationException e){
            return new ResponseEntity<>("Vec postoji zahtev sa ovim emailom", HttpStatus.CONFLICT);
        }

        return new ResponseEntity<>(zahtevZaRegistraciju, HttpStatus.CREATED);
    }

    @PostMapping("/aktivacijaPacijenta/{id}")
    public ResponseEntity aktivacijaPacijenta(@PathVariable Long id) {

        if (id == null){
            return  new ResponseEntity("Pogresno poslati podaci", HttpStatus.BAD_REQUEST);
        }
        ZahtevZaRegistraciju zahtevZaRegistraciju = new ZahtevZaRegistraciju();
        Pacijent pacijent;
        try {
           pacijent = zahtevZaRegistracijuService.aktivacijaPacijenta(id);
        } catch (ValidationException e){
            return new ResponseEntity<>("Ne postoji pacijent sa ovim idem", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        return new ResponseEntity<>(pacijent, HttpStatus.OK);
    }



    @PostMapping("/promeniLozinku")
 //   @PreAuthorize("hasAuthority('PACIJENT')")
    public ResponseEntity<?> changePassword(@RequestBody PasswordChanger passwordChanger) {


        if (passwordChanger == null){
            return new ResponseEntity<>("Uneli ste pograsne podatke", HttpStatus.BAD_REQUEST);
        }
        userDetailsService.changePassword(passwordChanger.oldPassword, passwordChanger.newPassword);

        Map<String, String> result = new HashMap<>();
        result.put("result", "success");
        return ResponseEntity.accepted().body(result);
    }

    static class PasswordChanger {
        public String oldPassword;
        public String newPassword;
    }

    }
