package ar.uba.dc.arcoirisui.gui.widget.dialog;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import ar.uba.dc.arcoirisui.gui.query.RepairStrategySearchCriteria;
import ar.uba.dc.arcoirisui.gui.widget.composite.query.RepairStrategiesSelectionQueryComposite;
import ar.uba.dc.arcoirisui.properties.ArcoIrisUIMessages;
import ar.uba.dc.arcoiris.selfhealing.StrategyTO;

import commons.gui.Advised;
import commons.gui.model.bean.BeanModel;
import commons.gui.util.PageHelper;
import commons.gui.widget.DefaultLayoutFactory;
import commons.gui.widget.composite.SimpleComposite;
import commons.gui.widget.creation.binding.BindingInfo;
import commons.gui.widget.creation.metainfo.TextFieldMetainfo;
import commons.gui.widget.factory.TextFactory;
import commons.properties.CommonLabels;

public class RepairStrategiesSelectionDialog extends Dialog implements Advised<RepairStrategiesSelectionQueryComposite> {

	private List<String> selectedRepairStrategyNames;

	private RepairStrategiesSelectionQueryComposite repairStrategiesSelectionQueryComposite;

	private RepairStrategySearchCriteria criteria;

	private final BeanModel<String> strategyCodeModel;

	public RepairStrategiesSelectionDialog() {
		this(new RepairStrategySearchCriteria());
	}

	public RepairStrategiesSelectionDialog(RepairStrategySearchCriteria criteria) {
		super(PageHelper.getMainShell());
		this.criteria = criteria;
		this.strategyCodeModel = new BeanModel<String>(String.class);
		this.selectedRepairStrategyNames = new ArrayList<String>();
	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText(ArcoIrisUIMessages.SELECT_REPAIR_STRATEGIES.toString());
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		super.createButtonsForButtonBar(parent);
		getButton(IDialogConstants.OK_ID).setEnabled(false);
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		SimpleComposite strategiesComposite = new SimpleComposite(parent, false, 2);

		repairStrategiesSelectionQueryComposite = new RepairStrategiesSelectionQueryComposite(this,
				strategiesComposite, this.criteria) {
			@Override
			protected ISelectionChangedListener getTableSelectionChangedListener() {
				ISelectionChangedListener listener = new ISelectionChangedListener() {
					public void selectionChanged(SelectionChangedEvent event) {
						Button acceptButton = getButton(IDialogConstants.OK_ID);
						if (acceptButton != null) {
							acceptButton.setEnabled(!getTable().getSelectedElements().isEmpty());
						}
						strategyCodeModel.setValue(getTable().getLastSelectedElement().toString());
					}
				};
				return listener;
			}
		};

		TextFieldMetainfo textMetainfo = TextFieldMetainfo.create(strategiesComposite, CommonLabels.NO_LABEL,
				new BindingInfo(this.strategyCodeModel), false);
		textMetainfo.multiline = true;
		textMetainfo.multiline_lines = 32; // hack
		TextFactory.createText(textMetainfo);

		DefaultLayoutFactory.setGrabAllExcessesAndFillBothGridData(strategiesComposite, true);
		return parent;
	}

	@Override
	protected void okPressed() {
		rowDoubleClicked(repairStrategiesSelectionQueryComposite);
	}

	public List<String> getSelectedRepairStrategyNames() {
		return selectedRepairStrategyNames;

	}

	public void setSelectedRepairStrategyNames(List<StrategyTO> strategiesTO) {
		this.selectedRepairStrategyNames.clear();
		for (StrategyTO strategyTO : strategiesTO) {
			this.selectedRepairStrategyNames.add(strategyTO.getName());
		}
	}

	/**
	 * @see jface.gui.gui.Advised#rowSelected(jface.gui.gui.widget.composite.QueryComposite))
	 */
	public void rowSelected(RepairStrategiesSelectionQueryComposite queryComposite) {
		this.setSelectedRepairStrategyNames(queryComposite.getSelectedElements());
		this.close();
	}

	public void rowDoubleClicked(RepairStrategiesSelectionQueryComposite queryComposite) {
		this.setSelectedRepairStrategyNames(queryComposite.getSelectedElements());
		this.close();
	}
}
