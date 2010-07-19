/*
 * This file is part of NavalPlan
 *
 * Copyright (C) 2009-2010 Fundación para o Fomento da Calidade Industrial e
 *                         Desenvolvemento Tecnolóxico de Galicia
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

package org.navalplanner.web.materials;

import static org.navalplanner.web.I18nHelper._;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.hibernate.validator.InvalidValue;
import org.navalplanner.business.common.daos.IConfigurationDAO;
import org.navalplanner.business.common.exceptions.InstanceNotFoundException;
import org.navalplanner.business.common.exceptions.ValidationException;
import org.navalplanner.business.materials.daos.IMaterialAssignmentDAO;
import org.navalplanner.business.materials.daos.IMaterialCategoryDAO;
import org.navalplanner.business.materials.daos.IMaterialDAO;
import org.navalplanner.business.materials.daos.IUnitTypeDAO;
import org.navalplanner.business.materials.entities.Material;
import org.navalplanner.business.materials.entities.MaterialCategory;
import org.navalplanner.business.materials.entities.UnitType;
import org.navalplanner.web.common.concurrentdetection.OnConcurrentModification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zkoss.ganttz.util.MutableTreeModel;

@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@OnConcurrentModification(goToPage = "/materials/materials.zul")
public class MaterialsModel implements IMaterialsModel {

    @Autowired
    IMaterialCategoryDAO categoryDAO;

    @Autowired
    IMaterialDAO materialDAO;

    @Autowired
    IUnitTypeDAO unitTypeDAO;

    @Autowired
    IConfigurationDAO configurationDAO;

    @Autowired
    IMaterialAssignmentDAO materialAssignmentDAO;

    MutableTreeModel<MaterialCategory> materialCategories = MutableTreeModel
            .create(MaterialCategory.class);

    private List<UnitType> unitTypes = new ArrayList<UnitType>();

    @Override
    @Transactional(readOnly=true)
    public MutableTreeModel<MaterialCategory> getMaterialCategories() {
        if (materialCategories.isEmpty()) {
            initializeMaterialCategories();
        }
        return materialCategories;
    }

    @Override
    @Transactional(readOnly=true)
    public void reloadMaterialCategories() {
        materialCategories = MutableTreeModel.create(MaterialCategory.class);
        initializeMaterialCategories();
    }

    private void initializeMaterialCategories() {
        final List<MaterialCategory> categories = categoryDAO.getAllRootMaterialCategories();
        for (MaterialCategory materialCategory: categories) {
            initializeMaterials(materialCategory.getMaterials());
            materialCategories.addToRoot(materialCategory);
            addCategories(materialCategory, materialCategory.getSubcategories());
        }
    }

    private void initializeMaterials(Set<Material> materials) {
        for (Material each: materials) {
            each.getDescription();
            if (each.getUnitType() != null) {
                each.getUnitType().getMeasure();
            }
        }
    }

    private void addCategories(MaterialCategory materialCategory, Set<MaterialCategory> categories) {
        for (MaterialCategory category: categories) {
            initializeMaterials(category.getMaterials());
            materialCategories.add(materialCategory, category);
            final Set<MaterialCategory> subcategories = category.getSubcategories();
            if (subcategories != null) {
                addCategories(category, subcategories);
            }
        }
    }

    @Override
    @Transactional(readOnly=true)
    public List<Material> getMaterials(MaterialCategory materialCategory) {
        List<Material> result = new ArrayList<Material>();
        result.addAll(materialCategory.getMaterials());
        return result;
    }

    @Override
    @Transactional(readOnly=true)
    public void addMaterialCategory(MaterialCategory parent, String categoryName) throws ValidationException {
        Validate.notNull(categoryName);

        Boolean generateCode = configurationDAO.getConfiguration().
            getGenerateCodeForMaterialCategories();
        MaterialCategory child;
        if(generateCode) {
            child = MaterialCategory.create(_(categoryName));
        }
        else {
            child = MaterialCategory.createUnvalidated("", _(categoryName));
        }
        child.setGenerateCode(generateCode);

        final MaterialCategory materialCategory = findMaterialCategory(child);
        if (materialCategory != null) {
            final InvalidValue invalidValue = new InvalidValue(_("{0} already exists", materialCategory.getName()),
                    MaterialCategory.class, "name", materialCategory.getName(), materialCategory);
            throw new ValidationException(invalidValue);
        }

        child.setParent(parent);
        if (parent == null) {
            materialCategories.addToRoot(child);
        } else {
            materialCategories.add(parent, child);
        }
    }

    private MaterialCategory findMaterialCategory(
            final MaterialCategory category) {
        for (MaterialCategory mc : materialCategories.asList()) {
            if (equalsMaterialCategory(mc, category)) {
                return mc;
            }
        }
        return null;
    }

    private boolean equalsMaterialCategory(MaterialCategory obj1, MaterialCategory obj2) {
        String name1 = StringUtils.deleteWhitespace(obj1.getName()
                .toLowerCase());
        String name2 = StringUtils.deleteWhitespace(obj2.getName()
                .toLowerCase());
        return name1.equals(name2);
    }

    @Override
    @Transactional
    public void confirmRemoveMaterialCategory(MaterialCategory materialCategory) {
        // Remove from list of material categories
        materialCategories.remove(materialCategory);

        // Remove from its parent
        final MaterialCategory parent = materialCategory.getParent();
        if (parent != null) {
            materialCategory.getParent().removeSubcategory(materialCategory);
        }

        final Long idMaterialCategory = materialCategory.getId();
        // It's not a yet-to-save element
        if (idMaterialCategory != null) {
            // It has a parent, in this case is enough with saving parent (all-delete-orphan)
            if (parent != null) {
                categoryDAO.save(materialCategory.getParent());
            } else {
                // It was a root element, should be deleted from DB
                try {
                    categoryDAO.remove(idMaterialCategory);
                } catch (InstanceNotFoundException e) {
                    throw new RuntimeException();
                }
            }
            reloadMaterialCategories();
        }
    }

    @Override
    public void addMaterialToMaterialCategory(MaterialCategory materialCategory) {
        Material material = Material.create("");
        material.setCategory(materialCategory);
        materialCategory.addMaterial(material);
    }

    @Override
    @Transactional
    public void confirmSave() throws ValidationException {
        final List<MaterialCategory> categories = materialCategories.asList();
        checkNoCodeRepeatedAtNewMaterials(categories);
        for (MaterialCategory each: categories) {
            categoryDAO.save(each);
        }
    }

    private void checkNoCodeRepeatedAtNewMaterials(
            final List<MaterialCategory> categories) throws ValidationException {
        List<Material> allMaterials = MaterialCategory
                .getAllMaterialsFrom(categories);
        Map<String, Material> byCode = new HashMap<String, Material>();
        for (Material each : allMaterials) {
            if (byCode.containsKey(each.getCode())) {
                throw new ValidationException(sameCodeMessage(each, byCode
                        .get(each.getCode())));
            }
            byCode.put(each.getCode(), each);
        }
    }

    private String sameCodeMessage(Material first, Material second) {
        return _(
                "both {0} of category {1} and {2} of category {3} have the same code",
                asStringForUser(first), first.getCategory().getName(),
                asStringForUser(second), second.getCategory().getName());
    }

    private String asStringForUser(Material material) {
        return String.format("{code: %s, description: %s}", material.getCode(),
                material
                .getDescription());
    }

    @Override
    public void removeMaterial(Material material) {
        material.getCategory().removeMaterial(material);
    }

    @Override
    @Transactional(readOnly = true)
    public Collection<? extends Material> getMaterials() {
        List<Material> result = new ArrayList<Material>();
        for (MaterialCategory each: materialCategories.asList()) {
            result.addAll(each.getMaterials());
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public void loadUnitTypes() {
        List<UnitType> result = new ArrayList<UnitType>();
        for (UnitType each : unitTypeDAO.findAll()) {
            each.getMeasure();
            result.add(each);
        }
        this.unitTypes = result;
    }

    public List<UnitType> getUnitTypes() {
        return this.unitTypes;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canRemoveMaterial(Material material) {
        if(material.isNewObject()) {
            return true;
        }
        return materialAssignmentDAO.getByMaterial(material).size() == 0;
    }
}
