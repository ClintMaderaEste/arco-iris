package commons.gui.table;

import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.widgets.TableColumn;

import commons.gui.widget.Alignment;
import commons.pref.PreferencesManager;
import commons.pref.domain.ColumnInfo;
import commons.pref.domain.TableInfo;
import commons.properties.EnumProperty;

public class SizeControlAdapter extends ControlAdapter {

	private final EnumProperty tableName;

	public SizeControlAdapter(EnumProperty tableName) {
		super();
		this.tableName = tableName;
	}

	@Override
	public void controlResized(ControlEvent event) {
		TableColumn tableColumn = (TableColumn) event.getSource();
		TableInfo tableInfo = PreferencesManager.getInstance().getTableInfo(this.tableName);
		ColumnInfo columnInfo = tableInfo.getColumnInfo((String) tableColumn.getData());
		columnInfo.setAlignment(Alignment.getAlignmentFrom(tableColumn.getStyle()));
		columnInfo.setWidth(tableColumn.getWidth());
	}
}