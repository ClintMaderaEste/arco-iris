package commons.datetime;

import java.io.Serializable;

import org.springframework.util.Assert;

public class Fecha implements Serializable {

	// TODO Mejorar las validaciones (se puede usar el Enum "Mes")
	public Fecha(int dia, int mes, int a�o) {
		super();
		validateDia(dia);
		this.dd = dia;
		validateMes(mes);
		this.mm = mes;
		validateA�o(a�o);
		this.aaaa = a�o;
	}

	public int getDia() {
		return dd;
	}

	public int getMes() {
		return mm;
	}

	public int getA�o() {
		return aaaa;
	}

	public void setDia(int dia) {
		validateDia(dia);
		this.dd = dia;
	}

	public void setMes(int mes) {
		validateMes(mes);
		this.mm = mes;
	}

	public void setA�o(int a�o) {
		validateA�o(a�o);
		this.aaaa = a�o;
	}

	@Override
	public String toString() {
		return String.format("%04d%02d%02d", aaaa, mm, dd);
	}

	private void validateDia(int dia) {
		Assert.isTrue(dia >= 1 || dia <= 31, "El d�a tiene que estar entre 1 y 31");
	}

	private void validateMes(int mes) {
		Assert.isTrue(mes >= 1 || mes <= 12, "El mes tiene que estar entre 1 y 12");
	}

	private void validateA�o(int a�o) {
		Assert.isTrue(a�o >= 1 || a�o <= 9999, "El a�o tiene que estar entre 1 y 9999");
	}

	private int dd;

	private int mm;

	private int aaaa;

	private static final long serialVersionUID = 1L;
}