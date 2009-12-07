package commons.gui.widget.factory;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

import commons.gui.widget.DefaultLayoutFactory;
import commons.gui.widget.composite.SimpleComposite;
import commons.gui.widget.creation.metainfo.BooleanFieldMetainfo;

public abstract class BooleanFactory {

	public static Control createBoolean(BooleanFieldMetainfo metainfo) {
		Composite booleanFieldComposite = new SimpleComposite(metainfo.composite,
				metainfo.readOnly, 2);
		final Button button = new Button(booleanFieldComposite, SWT.CHECK);
		LabelFactory.createLabel(booleanFieldComposite, metainfo.label, false, false);

		boolean value = Boolean.valueOf(metainfo.binding.getValue());
		button.setSelection(value);
		button.setEnabled(!metainfo.readOnly);
		metainfo.binding.bind(button);
		
		if(metainfo.horizontalSpan > 0){
			DefaultLayoutFactory.setGridHSpan(booleanFieldComposite, metainfo.horizontalSpan);
		}
		
		return button;
	}

}