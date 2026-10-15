package dev.legatus.server.web.rest;

import dev.legatus.server.repository.WorkerNodeRepository;
import dev.legatus.server.service.WorkerNodeQueryService;
import dev.legatus.server.service.WorkerNodeService;
import dev.legatus.server.service.criteria.WorkerNodeCriteria;
import dev.legatus.server.service.dto.WorkerNodeDTO;
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
 * REST controller for managing {@link dev.legatus.server.domain.WorkerNode}.
 */
@RestController
@RequestMapping("/api/worker-nodes")
public class WorkerNodeResource {

    private static final Logger LOG = LoggerFactory.getLogger(WorkerNodeResource.class);

    private static final String ENTITY_NAME = "workerNode";

    @Value("${jhipster.clientApp.name:legatusServer}")
    private String applicationName;

    private final WorkerNodeService workerNodeService;

    private final WorkerNodeRepository workerNodeRepository;

    private final WorkerNodeQueryService workerNodeQueryService;

    public WorkerNodeResource(
        WorkerNodeService workerNodeService,
        WorkerNodeRepository workerNodeRepository,
        WorkerNodeQueryService workerNodeQueryService
    ) {
        this.workerNodeService = workerNodeService;
        this.workerNodeRepository = workerNodeRepository;
        this.workerNodeQueryService = workerNodeQueryService;
    }

    /**
     * {@code POST  /worker-nodes} : Create a new workerNode.
     *
     * @param workerNodeDTO the workerNodeDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new workerNodeDTO, or with status {@code 400 (Bad Request)} if the workerNode already has an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<WorkerNodeDTO> createWorkerNode(@Valid @RequestBody WorkerNodeDTO workerNodeDTO) throws URISyntaxException {
        LOG.debug("REST request to save WorkerNode : {}", workerNodeDTO);
        if (workerNodeRepository.existsById(workerNodeDTO.getWorkerId())) {
            throw new BadRequestAlertException("workerNode already exists", ENTITY_NAME, "idexists");
        }
        workerNodeDTO = workerNodeService.save(workerNodeDTO);
        return ResponseEntity.created(new URI("/api/worker-nodes/" + workerNodeDTO.getWorkerId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, workerNodeDTO.getWorkerId()))
            .body(workerNodeDTO);
    }

    /**
     * {@code PUT  /worker-nodes/:workerId} : Updates an existing workerNode.
     *
     * @param workerId the id of the workerNodeDTO to save.
     * @param workerNodeDTO the workerNodeDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated workerNodeDTO,
     * or with status {@code 400 (Bad Request)} if the workerNodeDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the workerNodeDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{workerId}")
    public ResponseEntity<WorkerNodeDTO> updateWorkerNode(
        @PathVariable(value = "workerId", required = false) final String workerId,
        @Valid @RequestBody WorkerNodeDTO workerNodeDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update WorkerNode : {}, {}", workerId, workerNodeDTO);
        if (workerNodeDTO.getWorkerId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(workerId, workerNodeDTO.getWorkerId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!workerNodeRepository.existsById(workerId)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        workerNodeDTO = workerNodeService.update(workerNodeDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, workerNodeDTO.getWorkerId()))
            .body(workerNodeDTO);
    }

    /**
     * {@code PATCH  /worker-nodes/:workerId} : Partial updates given fields of an existing workerNode, field will ignore if it is null
     *
     * @param workerId the id of the workerNodeDTO to save.
     * @param workerNodeDTO the workerNodeDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated workerNodeDTO,
     * or with status {@code 400 (Bad Request)} if the workerNodeDTO is not valid,
     * or with status {@code 404 (Not Found)} if the workerNodeDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the workerNodeDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{workerId}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<WorkerNodeDTO> partialUpdateWorkerNode(
        @PathVariable(value = "workerId", required = false) final String workerId,
        @NotNull @RequestBody WorkerNodeDTO workerNodeDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partially update WorkerNode : {}, {}", workerId, workerNodeDTO);
        if (workerNodeDTO.getWorkerId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(workerId, workerNodeDTO.getWorkerId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!workerNodeRepository.existsById(workerId)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<WorkerNodeDTO> result = workerNodeService.partialUpdate(workerNodeDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, workerNodeDTO.getWorkerId())
        );
    }

    /**
     * {@code GET  /worker-nodes} : get all the Worker Nodes.
     *
     * @param pageable the pagination information.
     * @param criteria the criteria which the requested entities should match.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of Worker Nodes in body.
     */
    @GetMapping("")
    public ResponseEntity<List<WorkerNodeDTO>> getAllWorkerNodes(
        WorkerNodeCriteria criteria,
        @org.springdoc.core.annotations.ParameterObject Pageable pageable
    ) {
        LOG.debug("REST request to get WorkerNodes by criteria: {}", criteria);

        Page<WorkerNodeDTO> page = workerNodeQueryService.findByCriteria(criteria, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /worker-nodes/count} : count all the workerNodes.
     *
     * @param criteria the criteria which the requested entities should match.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the count in body.
     */
    @GetMapping("/count")
    public ResponseEntity<Long> countWorkerNodes(WorkerNodeCriteria criteria) {
        LOG.debug("REST request to count WorkerNodes by criteria: {}", criteria);
        return ResponseEntity.ok().body(workerNodeQueryService.countByCriteria(criteria));
    }

    /**
     * {@code GET  /worker-nodes/:id} : get the "id" workerNode.
     *
     * @param id the id of the workerNodeDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the workerNodeDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<WorkerNodeDTO> getWorkerNode(@PathVariable("id") String id) {
        LOG.debug("REST request to get WorkerNode : {}", id);
        Optional<WorkerNodeDTO> workerNodeDTO = workerNodeService.findOne(id);
        return ResponseUtil.wrapOrNotFound(workerNodeDTO);
    }

    /**
     * {@code DELETE  /worker-nodes/:id} : delete the "id" workerNode.
     *
     * @param id the id of the workerNodeDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWorkerNode(@PathVariable("id") String id) {
        LOG.debug("REST request to delete WorkerNode : {}", id);
        workerNodeService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id))
            .build();
    }
}
