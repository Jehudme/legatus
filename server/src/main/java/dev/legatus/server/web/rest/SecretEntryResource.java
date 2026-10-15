package dev.legatus.server.web.rest;

import dev.legatus.server.repository.SecretEntryRepository;
import dev.legatus.server.service.SecretEntryService;
import dev.legatus.server.service.dto.SecretEntryDTO;
import dev.legatus.server.web.rest.errors.BadRequestAlertException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link dev.legatus.server.domain.SecretEntry}.
 */
@RestController
@RequestMapping("/api/secret-entries")
public class SecretEntryResource {

    private static final Logger LOG = LoggerFactory.getLogger(SecretEntryResource.class);

    private static final String ENTITY_NAME = "secretEntry";

    @Value("${jhipster.clientApp.name:legatusServer}")
    private String applicationName;

    private final SecretEntryService secretEntryService;

    private final SecretEntryRepository secretEntryRepository;

    public SecretEntryResource(SecretEntryService secretEntryService, SecretEntryRepository secretEntryRepository) {
        this.secretEntryService = secretEntryService;
        this.secretEntryRepository = secretEntryRepository;
    }

    /**
     * {@code POST  /secret-entries} : Create a new secretEntry.
     *
     * @param secretEntryDTO the secretEntryDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new secretEntryDTO, or with status {@code 400 (Bad Request)} if the secretEntry already has an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<SecretEntryDTO> createSecretEntry(@Valid @RequestBody SecretEntryDTO secretEntryDTO) throws URISyntaxException {
        LOG.debug("REST request to save SecretEntry : {}", secretEntryDTO);
        if (secretEntryRepository.existsById(secretEntryDTO.getSecretKey())) {
            throw new BadRequestAlertException("secretEntry already exists", ENTITY_NAME, "idexists");
        }
        secretEntryDTO = secretEntryService.save(secretEntryDTO);
        return ResponseEntity.created(new URI("/api/secret-entries/" + secretEntryDTO.getSecretKey()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, secretEntryDTO.getSecretKey()))
            .body(secretEntryDTO);
    }

    /**
     * {@code PUT  /secret-entries/:secretKey} : Updates an existing secretEntry.
     *
     * @param secretKey the id of the secretEntryDTO to save.
     * @param secretEntryDTO the secretEntryDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated secretEntryDTO,
     * or with status {@code 400 (Bad Request)} if the secretEntryDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the secretEntryDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{secretKey}")
    public ResponseEntity<SecretEntryDTO> updateSecretEntry(
        @PathVariable(value = "secretKey", required = false) final String secretKey,
        @Valid @RequestBody SecretEntryDTO secretEntryDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update SecretEntry : {}, {}", secretKey, secretEntryDTO);
        if (secretEntryDTO.getSecretKey() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(secretKey, secretEntryDTO.getSecretKey())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!secretEntryRepository.existsById(secretKey)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        secretEntryDTO = secretEntryService.update(secretEntryDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, secretEntryDTO.getSecretKey()))
            .body(secretEntryDTO);
    }

    /**
     * {@code PATCH  /secret-entries/:secretKey} : Partial updates given fields of an existing secretEntry, field will ignore if it is null
     *
     * @param secretKey the id of the secretEntryDTO to save.
     * @param secretEntryDTO the secretEntryDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated secretEntryDTO,
     * or with status {@code 400 (Bad Request)} if the secretEntryDTO is not valid,
     * or with status {@code 404 (Not Found)} if the secretEntryDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the secretEntryDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{secretKey}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<SecretEntryDTO> partialUpdateSecretEntry(
        @PathVariable(value = "secretKey", required = false) final String secretKey,
        @NotNull @RequestBody SecretEntryDTO secretEntryDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partially update SecretEntry : {}, {}", secretKey, secretEntryDTO);
        if (secretEntryDTO.getSecretKey() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(secretKey, secretEntryDTO.getSecretKey())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!secretEntryRepository.existsById(secretKey)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<SecretEntryDTO> result = secretEntryService.partialUpdate(secretEntryDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, secretEntryDTO.getSecretKey())
        );
    }

    /**
     * {@code GET  /secret-entries} : get all the Secret Entries.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of Secret Entries in body.
     */
    @GetMapping("")
    public List<SecretEntryDTO> getAllSecretEntries() {
        LOG.debug("REST request to get all SecretEntries");
        return secretEntryService.findAll();
    }

    /**
     * {@code GET  /secret-entries/:id} : get the "id" secretEntry.
     *
     * @param id the id of the secretEntryDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the secretEntryDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<SecretEntryDTO> getSecretEntry(@PathVariable("id") String id) {
        LOG.debug("REST request to get SecretEntry : {}", id);
        Optional<SecretEntryDTO> secretEntryDTO = secretEntryService.findOne(id);
        return ResponseUtil.wrapOrNotFound(secretEntryDTO);
    }

    /**
     * {@code DELETE  /secret-entries/:id} : delete the "id" secretEntry.
     *
     * @param id the id of the secretEntryDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSecretEntry(@PathVariable("id") String id) {
        LOG.debug("REST request to delete SecretEntry : {}", id);
        secretEntryService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id))
            .build();
    }
}
