/*
 * This file is part of ###PROJECT_NAME###
 *
 * Copyright (C) 2009 Fundación para o Fomento da Calidade Industrial e
 *                    Desenvolvemento Tecnolóxico de Galicia
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.navalplanner.web.planner.allocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.hibernate.Hibernate;
import org.navalplanner.business.calendars.daos.IBaseCalendarDAO;
import org.navalplanner.business.common.ProportionalDistributor;
import org.navalplanner.business.orders.daos.IHoursGroupDAO;
import org.navalplanner.business.orders.entities.AggregatedHoursGroup;
import org.navalplanner.business.orders.entities.HoursGroup;
import org.navalplanner.business.orders.entities.TaskSource;
import org.navalplanner.business.planner.daos.ITaskElementDAO;
import org.navalplanner.business.planner.daos.ITaskSourceDAO;
import org.navalplanner.business.planner.entities.DayAssignment;
import org.navalplanner.business.planner.entities.DerivedAllocation;
import org.navalplanner.business.planner.entities.GenericResourceAllocation;
import org.navalplanner.business.planner.entities.ResourceAllocation;
import org.navalplanner.business.planner.entities.Task;
import org.navalplanner.business.planner.entities.TaskElement;
import org.navalplanner.business.planner.entities.DerivedAllocationGenerator.IWorkerFinder;
import org.navalplanner.business.resources.daos.ICriterionDAO;
import org.navalplanner.business.resources.daos.IResourceDAO;
import org.navalplanner.business.resources.entities.Criterion;
import org.navalplanner.business.resources.entities.CriterionSatisfaction;
import org.navalplanner.business.resources.entities.CriterionType;
import org.navalplanner.business.resources.entities.Machine;
import org.navalplanner.business.resources.entities.MachineWorkersConfigurationUnit;
import org.navalplanner.business.resources.entities.Resource;
import org.navalplanner.business.resources.entities.Worker;
import org.navalplanner.web.planner.order.PlanningState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zkoss.ganttz.extensions.IContextWithPlannerTask;

/**
 * Model for UI operations related to {@link Task}
 * @author Manuel Rego Casasnovas <mrego@igalia.com>
 * @author Diego Pino García <dpino@igalia.com>
 */
@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ResourceAllocationModel implements IResourceAllocationModel {

    @Autowired
    private ITaskElementDAO taskElementDAO;

    @Autowired
    private IResourceDAO resourceDAO;

    @Autowired
    private IHoursGroupDAO hoursGroupDAO;

    @Autowired
    private ITaskSourceDAO taskSourceDAO;

    private Task task;

    @Autowired
    private IBaseCalendarDAO calendarDAO;

    @Autowired
    private ICriterionDAO criterionDAO;

    private PlanningState planningState;

    private AllocationRowsHandler allocationRowsHandler;

    private IContextWithPlannerTask<TaskElement> context;

    @Override
    @Transactional(readOnly = true)
    public void addSpecific(Collection<? extends Resource> resources) {
        reassociateResourcesWithSession();
        allocationRowsHandler
                .addSpecificResourceAllocationFor(reloadResources(resources));
    }

    private List<Resource> reloadResources(
            Collection<? extends Resource> resources) {
        List<Resource> result = new ArrayList<Resource>();
        for (Resource each : resources) {
            Resource reloaded = resourceDAO.findExistingEntity(each.getId());
            reattachResource(reloaded);
            result.add(reloaded);
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public ProportionalDistributor addDefaultAllocations() {
        reassociateResourcesWithSession();
        List<AggregatedHoursGroup> hoursGroups = task
                .getAggregatedByCriterions();
        int hours[] = new int[hoursGroups.size()];
        int i = 0;
        for (AggregatedHoursGroup each : hoursGroups) {
            hours[i++] = each.getHours();
            List<Resource> resourcesFound = resourceDAO
                    .findAllSatisfyingCriterions(each.getCriterions());
            allocationRowsHandler.addGeneric(each.getCriterions(),
                    reloadResources(resourcesFound), each.getHours());
        }
        return ProportionalDistributor.create(hours);
    }

    @Override
    @Transactional(readOnly = true)
    public void addGeneric(Set<Criterion> criterions,
            Collection<? extends Resource> resourcesMatched) {
        reassociateResourcesWithSession();
        List<Resource> reloadResources = reloadResources(resourcesMatched);
        allocationRowsHandler.addGeneric(criterions, reloadResources);
    }

    @Override
    public void cancel() {
        allocationRowsHandler = null;
    }

    @Override
    @Transactional(readOnly = true)
    public void accept() {
        stepsBeforeDoingAllocation();
        applyAllocationResult(allocationRowsHandler.doAllocation());
    }

    @Override
    @Transactional(readOnly = true)
    public void accept(AllocationResult modifiedAllocationResult) {
        stepsBeforeDoingAllocation();
        applyAllocationResult(modifiedAllocationResult);
    }

    @Override
    @Transactional(readOnly = true)
    public <T> T onAllocationContext(
            IResourceAllocationContext<T> resourceAllocationContext) {
        reattachmentsBeforeDoingAllocation();
        return resourceAllocationContext.doInsideTransaction();
    }

    private void ensureResourcesAreReadyForDoingAllocation() {
        Set<Resource> resources = allocationRowsHandler
                .getAllocationResources();
        for (Resource each : resources) {
            reattachResource(each);
        }
    }

    private void stepsBeforeDoingAllocation() {
        reattachmentsBeforeDoingAllocation();
        removeDeletedAllocations();
    }

    private void reattachmentsBeforeDoingAllocation() {
        ensureResourcesAreReadyForDoingAllocation();
        if (task.getCalendar() != null) {
            calendarDAO.reattachUnmodifiedEntity(task.getCalendar());
        }
    }

    private void reassociateResourcesWithSession() {
        planningState.reassociateResourcesWithSession(resourceDAO);
    }

    private void removeDeletedAllocations() {
        Set<ResourceAllocation<?>> allocationsRequestedToRemove = allocationRowsHandler
                .getAllocationsRequestedToRemove();
        for (ResourceAllocation<?> resourceAllocation : allocationsRequestedToRemove) {
            task.removeResourceAllocation(resourceAllocation);
        }
    }

    private void applyAllocationResult(AllocationResult allocationResult) {
        org.zkoss.ganttz.data.Task ganttTask = context.getTask();
        Date previousStartDate = ganttTask.getBeginDate();
        long previousLength = ganttTask.getLengthMilliseconds();
        allocationResult.applyTo(task);
        ganttTask.fireChangesForPreviousValues(previousStartDate,
                previousLength);
        ganttTask.reloadComponent();
        context.reloadCharts();
    }

    @Override
    @Transactional(readOnly = true)
    public AllocationRowsHandler initAllocationsFor(Task task,
            IContextWithPlannerTask<TaskElement> context,
            PlanningState planningState) {
        this.context = context;
        this.task = task;
        this.planningState = planningState;
        planningState.reassociateResourcesWithSession(resourceDAO);
        taskElementDAO.reattach(this.task);
        reattachTaskSource();
        loadCriterionsOfGenericAllocations();
        reattachHoursGroup(this.task.getHoursGroup());
        reattachCriterions(this.task.getHoursGroup().getValidCriterions());
        loadResources(this.task.getResourceAllocations());
        loadDerivedAllocations(this.task.getResourceAllocations());
        List<AllocationRow> initialRows = AllocationRow.toRows(this.task
                .getResourceAllocations());
        allocationRowsHandler = AllocationRowsHandler.create(task, initialRows,
                createWorkerFinder());
        return allocationRowsHandler;
    }

    private IWorkerFinder createWorkerFinder() {
        return new IWorkerFinder() {

            @Override
            public Collection<Worker> findWorkersMatching(
                    Collection<? extends Criterion> requiredCriterions) {
                reassociateResourcesWithSession();
                List<Resource> allSatisfyingCriterions;
                if (!requiredCriterions.isEmpty()) {
                    allSatisfyingCriterions = resourceDAO
                            .findAllSatisfyingCriterions(requiredCriterions);
                } else {
                    allSatisfyingCriterions = new ArrayList<Resource>();
                }
                return Resource.workers(reloadResources(Resource
                        .workers(allSatisfyingCriterions)));
            }
        };
    }

    private void loadCriterionsOfGenericAllocations() {
        Set<ResourceAllocation<?>> resourceAllocations = this.task
                .getResourceAllocations();
        for (ResourceAllocation<?> resourceAllocation : resourceAllocations) {
            if (resourceAllocation instanceof GenericResourceAllocation) {
                GenericResourceAllocation generic = (GenericResourceAllocation) resourceAllocation;
                generic.getCriterions().size();
            }
        }
    }

    private void reattachHoursGroup(HoursGroup hoursGroup) {
        hoursGroupDAO.reattachUnmodifiedEntity(hoursGroup);
        hoursGroup.getPercentage();
        reattachCriterions(hoursGroup.getValidCriterions());
    }

    private void reattachCriterions(Set<Criterion> criterions) {
        for (Criterion criterion : criterions) {
            reattachCriterion(criterion);
        }
    }

    private void loadResources(Set<ResourceAllocation<?>> resourceAllocations) {
        for (ResourceAllocation<?> each : resourceAllocations) {
            each.getAssociatedResources();
        }
    }

    private void loadMachine(Machine eachMachine) {
        for (MachineWorkersConfigurationUnit eachUnit : eachMachine
                .getConfigurationUnits()) {
            Hibernate.initialize(eachUnit);
        }
    }

    private void loadDerivedAllocations(
            Set<ResourceAllocation<?>> resourceAllocations) {
        for (ResourceAllocation<?> each : resourceAllocations) {
            for (DerivedAllocation eachDerived : each.getDerivedAllocations()) {
                Hibernate.initialize(eachDerived);
                eachDerived.getAssignments();
                eachDerived.getAlpha();
                eachDerived.getName();
            }
        }
    }

    private void reattachTaskSource() {
        TaskSource taskSource = task.getTaskSource();
        taskSourceDAO.reattach(taskSource);
        Set<HoursGroup> hoursGroups = taskSource.getHoursGroups();
        for (HoursGroup hoursGroup : hoursGroups) {
            reattachHoursGroup(hoursGroup);
        }
    }

    private void reattachCriterion(Criterion criterion) {
        criterionDAO.reattachUnmodifiedEntity(criterion);
        criterion.getName();
        reattachCriterionType(criterion.getType());
    }

    private void reattachCriterionType(CriterionType criterionType) {
        criterionType.getName();
    }

    private void reattachResource(Resource resource) {
        resourceDAO.reattach(resource);
        reattachCriterionSatisfactions(resource.getCriterionSatisfactions());
        if (resource.getCalendar() != null) {
            calendarDAO.reattachUnmodifiedEntity(resource.getCalendar());
        }
        for (DayAssignment dayAssignment : resource.getAssignments()) {
            Hibernate.initialize(dayAssignment);
        }
        if (resource instanceof Machine) {
            loadMachine((Machine) resource);
        }
    }

    private void reattachCriterionSatisfactions(
            Set<CriterionSatisfaction> criterionSatisfactions) {
        for (CriterionSatisfaction criterionSatisfaction : criterionSatisfactions) {
            criterionSatisfaction.getStartDate();
            reattachCriterion(criterionSatisfaction.getCriterion());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<AggregatedHoursGroup> getHoursAggregatedByCriterions() {
        reattachTaskSource();
        List<AggregatedHoursGroup> result = task.getTaskSource()
                .getAggregatedByCriterions();
        ensuringAccesedPropertiesAreLoaded(result);
        return result;
    }

    private void ensuringAccesedPropertiesAreLoaded(
            List<AggregatedHoursGroup> result) {
        for (AggregatedHoursGroup each : result) {
            each.getCriterionsJoinedByComma();
            each.getHours();
        }
    }

    @Override
    public Integer getOrderHours() {
        if (task == null) {
            return 0;
        }
        return AggregatedHoursGroup.sum(task.getAggregatedByCriterions());
    }

}
