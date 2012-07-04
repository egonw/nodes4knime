/*
 * Copyright (c) 2012, Stephan Beisken (sbeisken@gmail.com). All rights reserved.
 * 
 * This file is part of the KNIME CDK plugin.
 * 
 * The KNIME CDK plugin is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 * 
 * The KNIME CDK plugin is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the
 * implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License along with the plugin. If not, see
 * <http://www.gnu.org/licenses/>.
 */
package org.openscience.cdk.knime.whim3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataType;
import org.knime.core.data.RowKey;
import org.knime.core.data.collection.CollectionCellFactory;
import org.knime.core.data.container.CellFactory;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.node.ExecutionMonitor;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.graph.ConnectivityChecker;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.knime.type.CDKValue;
import org.openscience.cdk.qsar.DescriptorValue;
import org.openscience.cdk.qsar.IMolecularDescriptor;
import org.openscience.cdk.qsar.descriptors.molecular.WHIMDescriptor;
import org.openscience.cdk.qsar.result.DoubleArrayResult;

/**
 * Cell factory for the moleculer WHIM descriptors.
 * 
 * @author Stephan Beisken, European Bioinformatics Institute
 */
public class Whim3dGenerator implements CellFactory {

	private final int molColIndex;
	private final DataColumnSpec[] dataColumnSpec;
	private List<Whim3dSchemes> weightingSchemes;

	private final IMolecularDescriptor whimDescriptor;

	/**
	 * Constructs the cell factory.
	 * 
	 * @param dataColumnSpec the data column output specification
	 * @param molColIndex the CDK molecule column index
	 */
	public Whim3dGenerator(int molColIndex, DataColumnSpec[] dataColumnSpec) {

		this.molColIndex = molColIndex;
		this.dataColumnSpec = dataColumnSpec;

		this.whimDescriptor = new WHIMDescriptor();
		getWeightingSchemes();
	}

	private void getWeightingSchemes() {

		weightingSchemes = new ArrayList<Whim3dSchemes>();

		for (DataColumnSpec outputColumnSpec : dataColumnSpec) {

			if (outputColumnSpec.getName().equals(Whim3dSchemes.UNITY_WEIGHTS.getTitle()))
				weightingSchemes.add(Whim3dSchemes.UNITY_WEIGHTS);
			if (outputColumnSpec.getName().equals(Whim3dSchemes.ATOMIC_MASSES.getTitle()))
				weightingSchemes.add(Whim3dSchemes.ATOMIC_MASSES);
			if (outputColumnSpec.getName().equals(Whim3dSchemes.ATOMIC_POLARIZABILITIES.getTitle()))
				weightingSchemes.add(Whim3dSchemes.ATOMIC_POLARIZABILITIES);
			if (outputColumnSpec.getName().equals(Whim3dSchemes.VdW_VOLUMES.getTitle()))
				weightingSchemes.add(Whim3dSchemes.VdW_VOLUMES);
			if (outputColumnSpec.getName().equals(Whim3dSchemes.ATOMIC_ELECTRONEGATIVITIES.getTitle()))
				weightingSchemes.add(Whim3dSchemes.ATOMIC_ELECTRONEGATIVITIES);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public DataCell[] getCells(DataRow row) {

		DataCell cdkCell = row.getCell(molColIndex);
		DataCell[] whimValueCells = new DataCell[dataColumnSpec.length];

		if (cdkCell.isMissing()) {
			Arrays.fill(whimValueCells, DataType.getMissingCell());
			return whimValueCells;
		}

		checkIsCdkCell(cdkCell);

		IAtomContainer molecule = ((CDKValue) row.getCell(molColIndex)).getAtomContainer();
		if (!ConnectivityChecker.isConnected(molecule))
			molecule = ConnectivityChecker.partitionIntoMolecules(molecule).getAtomContainer(0);
		
		whimValueCells = calculateWhimValues(molecule);

		return whimValueCells;
	}

	private void checkIsCdkCell(DataCell dataCell) {

		if (!(dataCell instanceof CDKValue)) {
			throw new IllegalArgumentException("No CDK cell at " + dataCell + ": " + dataCell.getClass().getName());
		}
	}

	private DataCell[] calculateWhimValues(IAtomContainer molecule) {

		DataCell[] whimValueCells = new DataCell[dataColumnSpec.length];

		int cellIndex = 0;
		for (Whim3dSchemes weightingScheme : weightingSchemes) {
			whimValueCells[cellIndex] = calculateValueForScheme(weightingScheme, molecule);
			cellIndex++;
		}

		return whimValueCells;
	}

	private DataCell calculateValueForScheme(Whim3dSchemes scheme, IAtomContainer molecule) {

		try {
			Object[] whimParameter = new String[] { scheme.getParameterName() };
			whimDescriptor.setParameters(whimParameter);
		} catch (CDKException exception) {
			return DataType.getMissingCell();
		}

		DescriptorValue whimValue = whimDescriptor.calculate(molecule);
		DoubleArrayResult whimResultArray = (DoubleArrayResult) whimValue.getValue();

		return getDataCell(whimResultArray);
	}

	private DataCell getDataCell(DoubleArrayResult whimResultArray) {

		Collection<DoubleCell> resultCol = new ArrayList<DoubleCell>();
		for (int i = 0; i < whimResultArray.length(); i++) {
			double res = whimResultArray.get(i);
			resultCol.add(new DoubleCell(res));
		}
		DataCell cell = CollectionCellFactory.createListCell(resultCol);

		return cell;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public DataColumnSpec[] getColumnSpecs() {

		return dataColumnSpec;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setProgress(int curRowNr, int rowCount, RowKey lastKey, ExecutionMonitor exec) {

		exec.setProgress(curRowNr / (double) rowCount, "Calculated WHIM for row " + curRowNr + " (\"" + lastKey + "\")");
	}
}
