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

package org.navalplanner.ws.labels.api;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.navalplanner.business.labels.entities.Label;
import org.navalplanner.ws.common.api.IntegrationEntityDTO;

/**
 * DTO for {@link Label} entity.
 *
 * @author Manuel Rego Casasnovas <mrego@igalia.com>
 */
@XmlRootElement(name = "label")
public class LabelDTO extends IntegrationEntityDTO {

    public final static String ENTITY_TYPE = "label";

    @XmlAttribute
    public String name;

    public LabelDTO() {
    }

    public LabelDTO(String code, String name) {
        super(code);
        this.name = name;
    }

    public LabelDTO(String name) {
        this(generateCode(), name);
    }

    @Override
    public String getEntityType() {
        return ENTITY_TYPE;
    }

}