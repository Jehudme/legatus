package dev.legatus.server.web.rest;

import dev.legatus.server.repository.SolixFunctionRepository;
import dev.legatus.server.service.SolixFunctionService;
import dev.legatus.server.service.dto.SolixFunctionDTO;
import dev.legatus.server.web.rest.errors.BadRequestAlertException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.PaginationUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link dev.legatus.server.domain.SolixFunction}.
 */
@RestController
@RequestMapping("/api/solix-functions")
public class SolixFunctionResource {

    private static final Logger LOG = LoggerFactory.getLogger(SolixFunctionResource.class);

    private static final String ENTITY_NAME = "solixFunction";

    @Value("${jhipster.clientApp.name:legatusServer}")
    private String applicationName;

    private final SolixFunctionService solixFunctionService;

    private final SolixFunctionRepository solixFunctionRepository;

    public SolixFunctionResource(SolixFunctionService solixFunctionService, SolixFunctionRepository solixFunctionRepository) {
        this.solixFunctionService = solixFunctionService;
        this.solixFunctionRepository = solixFunctionRepository;
    }

    /**
     * {@code POST  /solix-functions} : Create a new solixFunction.
     *
     * @param solixFunctionDTO the solixFunctionDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new solixFunctionDTO, or with status {@code 400 (Bad Request)} if the solixFunction already has an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<SolixFunctionDTO> createSolixFunction(@Valid @RequestBody SolixFunctionDTO solixFunctionDTO)
        throws URISyntaxException {
        LOG.debug("REST request to save SolixFunction : {}", solixFunctionDTO);
        if (solixFunctionDTO.getFunctionId() != null) {
            throw new BadRequestAlertException("A new solixFunction cannot already have an ID", ENTITY_NAME, "idexists");
        }
        solixFunctionDTO = solixFunctionService.save(solixFunctionDTO);
        return ResponseEntity.created(new URI("/api/solix-functions/" + solixFunctionDTO.getFunctionId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, solixFunctionDTO.getFunctionId().toString()))
            .body(solixFunctionDTO);
    }

    /**
     * {@code PUT  /solix-functions/:functionId} : Updates an existing solixFunction.
     *
     * @param functionId the id of the solixFunctionDTO to save.
     * @param solixFunctionDTO the solixFunctionDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated solixFunctionDTO,
     * or with status {@code 400 (Bad Request)} if the solixFunctionDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the solixFunctionDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{functionId}")
    public ResponseEntity<SolixFunctionDTO> updateSolixFunction(
        @PathVariable(value = "functionId", required = false) final UUID functionId,
        @Valid @RequestBody SolixFunctionDTO solixFunctionDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update SolixFunction : {}, {}", functionId, solixFunctionDTO);
        if (solixFunctionDTO.getFunctionId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(functionId, solixFunctionDTO.getFunctionId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!solixFunctionRepository.existsById(functionId)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        solixFunctionDTO = solixFunctionService.update(solixFunctionDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, solixFunctionDTO.getFunctionId().toString()))
            .body(solixFunctionDTO);
    }

    /**
     * {@code PATCH  /solix-functions/:functionId} : Partial updates given fields of an existing solixFunction, field will ignore if it is null
     *
     * @param functionId the id of the solixFunctionDTO to save.
     * @param solixFunctionDTO the solixFunctionDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated solixFunctionDTO,
     * or with status {@code 400 (Bad Request)} if the solixFunctionDTO is not valid,
     * or with status {@code 404 (Not Found)} if the solixFunctionDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the solixFunctionDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{functionId}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<SolixFunctionDTO> partialUpdateSolixFunction(
        @PathVariable(value = "functionId", required = false) final UUID functionId,
        @NotNull @RequestBody SolixFunctionDTO solixFunctionDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partially update SolixFunction : {}, {}", functionId, solixFunctionDTO);
        if (solixFunctionDTO.getFunctionId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(functionId, solixFunctionDTO.getFunctionId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!solixFunctionRepository.existsById(functionId)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<SolixFunctionDTO> result = solixFunctionService.partialUpdate(solixFunctionDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, solixFunctionDTO.getFunctionId().toString())
        );
    }

    /**
     * {@code GET  /solix-functions} : get all the Solix Functions.
     *
     * @param pageable the pagination information.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of Solix Functions in body.
     */
    @GetMapping("")
    public ResponseEntity<List<SolixFunctionDTO>> getAllSolixFunctions(@org.springdoc.core.annotations.ParameterObject Pageable pageable) {
        LOG.debug("REST request to get a page of SolixFunctions");
        Page<SolixFunctionDTO> page = solixFunctionService.findAll(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /solix-functions/:id} : get the "id" solixFunction.
     *
     * @param id the id of the solixFunctionDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the solixFunctionDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<SolixFunctionDTO> getSolixFunction(@PathVariable("id") UUID id) {
        LOG.debug("REST request to get SolixFunction : {}", id);
        Optional<SolixFunctionDTO> solixFunctionDTO = solixFunctionService.findOne(id);
        return ResponseUtil.wrapOrNotFound(solixFunctionDTO);
    }

    /**
     * {@code DELETE  /solix-functions/:id} : delete the "id" solixFunction.
     *
     * @param id the id of the solixFunctionDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSolixFunction(@PathVariable("id") UUID id) {
        LOG.debug("REST request to delete SolixFunction : {}", id);
        solixFunctionService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }
}
