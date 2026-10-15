package dev.legatus.server.web.rest;

import dev.legatus.server.repository.ComputeTaskRepository;
import dev.legatus.server.service.ComputeTaskQueryService;
import dev.legatus.server.service.ComputeTaskService;
import dev.legatus.server.service.criteria.ComputeTaskCriteria;
import dev.legatus.server.service.dto.ComputeTaskDTO;
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
 * REST controller for managing {@link dev.legatus.server.domain.ComputeTask}.
 */
@RestController
@RequestMapping("/api/compute-tasks")
public class ComputeTaskResource {

    private static final Logger LOG = LoggerFactory.getLogger(ComputeTaskResource.class);

    private static final String ENTITY_NAME = "computeTask";

    @Value("${jhipster.clientApp.name:legatusServer}")
    private String applicationName;

    private final ComputeTaskService computeTaskService;

    private final ComputeTaskRepository computeTaskRepository;

    private final ComputeTaskQueryService computeTaskQueryService;

    public ComputeTaskResource(
        ComputeTaskService computeTaskService,
        ComputeTaskRepository computeTaskRepository,
        ComputeTaskQueryService computeTaskQueryService
    ) {
        this.computeTaskService = computeTaskService;
        this.computeTaskRepository = computeTaskRepository;
        this.computeTaskQueryService = computeTaskQueryService;
    }

    /**
     * {@code POST  /compute-tasks} : Create a new computeTask.
     *
     * @param computeTaskDTO the computeTaskDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new computeTaskDTO, or with status {@code 400 (Bad Request)} if the computeTask already has an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<ComputeTaskDTO> createComputeTask(@Valid @RequestBody ComputeTaskDTO computeTaskDTO) throws URISyntaxException {
        LOG.debug("REST request to save ComputeTask : {}", computeTaskDTO);
        if (computeTaskRepository.existsById(computeTaskDTO.getTaskId())) {
            throw new BadRequestAlertException("computeTask already exists", ENTITY_NAME, "idexists");
        }
        computeTaskDTO = computeTaskService.save(computeTaskDTO);
        return ResponseEntity.created(new URI("/api/compute-tasks/" + computeTaskDTO.getTaskId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, computeTaskDTO.getTaskId()))
            .body(computeTaskDTO);
    }

    /**
     * {@code PUT  /compute-tasks/:taskId} : Updates an existing computeTask.
     *
     * @param taskId the id of the computeTaskDTO to save.
     * @param computeTaskDTO the computeTaskDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated computeTaskDTO,
     * or with status {@code 400 (Bad Request)} if the computeTaskDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the computeTaskDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{taskId}")
    public ResponseEntity<ComputeTaskDTO> updateComputeTask(
        @PathVariable(value = "taskId", required = false) final String taskId,
        @Valid @RequestBody ComputeTaskDTO computeTaskDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update ComputeTask : {}, {}", taskId, computeTaskDTO);
        if (computeTaskDTO.getTaskId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(taskId, computeTaskDTO.getTaskId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!computeTaskRepository.existsById(taskId)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        computeTaskDTO = computeTaskService.update(computeTaskDTO);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, computeTaskDTO.getTaskId()))
            .body(computeTaskDTO);
    }

    /**
     * {@code PATCH  /compute-tasks/:taskId} : Partial updates given fields of an existing computeTask, field will ignore if it is null
     *
     * @param taskId the id of the computeTaskDTO to save.
     * @param computeTaskDTO the computeTaskDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated computeTaskDTO,
     * or with status {@code 400 (Bad Request)} if the computeTaskDTO is not valid,
     * or with status {@code 404 (Not Found)} if the computeTaskDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the computeTaskDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{taskId}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<ComputeTaskDTO> partialUpdateComputeTask(
        @PathVariable(value = "taskId", required = false) final String taskId,
        @NotNull @RequestBody ComputeTaskDTO computeTaskDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partially update ComputeTask : {}, {}", taskId, computeTaskDTO);
        if (computeTaskDTO.getTaskId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(taskId, computeTaskDTO.getTaskId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!computeTaskRepository.existsById(taskId)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<ComputeTaskDTO> result = computeTaskService.partialUpdate(computeTaskDTO);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, computeTaskDTO.getTaskId())
        );
    }

    /**
     * {@code GET  /compute-tasks} : get all the Compute Tasks.
     *
     * @param pageable the pagination information.
     * @param criteria the criteria which the requested entities should match.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of Compute Tasks in body.
     */
    @GetMapping("")
    public ResponseEntity<List<ComputeTaskDTO>> getAllComputeTasks(
        ComputeTaskCriteria criteria,
        @org.springdoc.core.annotations.ParameterObject Pageable pageable
    ) {
        LOG.debug("REST request to get ComputeTasks by criteria: {}", criteria);

        Page<ComputeTaskDTO> page = computeTaskQueryService.findByCriteria(criteria, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /compute-tasks/count} : count all the computeTasks.
     *
     * @param criteria the criteria which the requested entities should match.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the count in body.
     */
    @GetMapping("/count")
    public ResponseEntity<Long> countComputeTasks(ComputeTaskCriteria criteria) {
        LOG.debug("REST request to count ComputeTasks by criteria: {}", criteria);
        return ResponseEntity.ok().body(computeTaskQueryService.countByCriteria(criteria));
    }

    /**
     * {@code GET  /compute-tasks/:id} : get the "id" computeTask.
     *
     * @param id the id of the computeTaskDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the computeTaskDTO, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ComputeTaskDTO> getComputeTask(@PathVariable("id") String id) {
        LOG.debug("REST request to get ComputeTask : {}", id);
        Optional<ComputeTaskDTO> computeTaskDTO = computeTaskService.findOne(id);
        return ResponseUtil.wrapOrNotFound(computeTaskDTO);
    }

    /**
     * {@code DELETE  /compute-tasks/:id} : delete the "id" computeTask.
     *
     * @param id the id of the computeTaskDTO to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteComputeTask(@PathVariable("id") String id) {
        LOG.debug("REST request to delete ComputeTask : {}", id);
        computeTaskService.delete(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id))
            .build();
    }
}
