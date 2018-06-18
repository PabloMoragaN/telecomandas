package edu.uclm.esi.disoft.comandas.dominio;

import java.util.Enumeration;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

import org.bson.types.ObjectId;
import org.json.JSONArray;
import org.json.JSONObject;

import edu.uclm.esi.disoft.comandas.dao.DAOCategoria;
import edu.uclm.esi.disoft.comandas.dao.DAOMesa;
import edu.uclm.esi.disoft.comandas.etiquetas.BSONeador;
import edu.uclm.esi.disoft.comandas.ws.ServidorWS;

public class Manager {
	//<integer,mesa>
	private ConcurrentHashMap<Object, Object> mesas;
	private JSONArray jsaCategorias;
	//<string,categoria>
	private ConcurrentHashMap<Object, Object> categorias;
	
	private Manager() throws InstantiationException, IllegalAccessException, NoSuchFieldException, SecurityException, Exception { //dejamos de usar los dao para hacerlo genérico con bsoneador
		mesas=BSONeador.load(Mesa.class);
		cargarCategorias();
	}
	
	private void cargarCategorias() throws InstantiationException, IllegalAccessException, NoSuchFieldException, SecurityException, Exception {
		categorias=BSONeador.load(Categoria.class);
		this.jsaCategorias=new JSONArray();
		Enumeration<Object> keys=categorias.keys();
		while (keys.hasMoreElements()) {
			String key=keys.nextElement().toString();
			Categoria categoria=(Categoria) categorias.get(key);
			this.jsaCategorias.put(categoria.toJSONObject());
		}
	}

	private static class ManagerHolder{
		static Manager singleton=new Manager();
	}
	
	public static Manager get() {
		return ManagerHolder.singleton;
	}
	
	public void abrirMesa(int id) throws Exception {
		Mesa mesa=(Mesa) mesas.get(id);
		mesa.abrir();
	}
	
	public void cerrarMesa(int id) throws Exception {
		Mesa mesa=(Mesa) mesas.get(id);
		String idComanda = mesa.getComandaActual().get_id();
		mesa.cerrar();
		if(idComanda != null) {
			JSONObject jso = new JSONObject();
			jso.put("type", "cerrar");
			jso.put("idComanda", idComanda);
			ServidorWS.enviar(jso);
		}
	}
	
	public void recibirComanda(int idMesa, JSONArray platos) throws Exception { //antiguo addToComanda
		Vector<PlatoPedido> platosNuevos = new Vector<>();
		Mesa mesa=(Mesa) mesas.get(idMesa);
		if (mesa.estaLibre())
			throw new Exception("La mesa " + idMesa + " está libre. Ábrela primero");
		for (int i=0; i<platos.length(); i++) {
			JSONObject jsoPlato=platos.getJSONObject(i);
			String idCategoria=jsoPlato.getString("idCategoria");
			String idPlato=jsoPlato.getString("idPlato");
			int unidades=jsoPlato.getInt("unidades");
			Categoria categoria=(Categoria) this.categorias.get(idCategoria);
			Plato plato=categoria.find(idPlato);
			platosNuevos.add(mesa.addToComanda(plato, unidades));
		}
		mesa.setPrecioComanda();
		
		if(mesa.getComandaActual().get_id() == null) {
			ObjectId id = new ObjectId();
			mesa.getComandaActual().set_id(id.toHexString());
			BSONeador.insert(mesa.getComandaActual());
		} else
			BSONeador.update(mesa.getComandaActual());
		
		solicitarPlatosCocina(mesa.getComandaActual().get_id(), idMesa, platosNuevos);
	}
	
	public void solicitarPlatosCocina(String idComanda, int idMesa, Vector<PlatoPedido> platos) {
		JSONObject jso = new JSONObject();
		jso.put("type", "platos");
		jso.put("idComanda", idComanda);
		jso.put("idMesa", idMesa);
		JSONArray jsa = new JSONArray();
		for(PlatoPedido pp : platos) {
			jsa.put(pp.toJSONObject());
		}
		//enviamos a cocina a través del ws
		jso.put("platos", jsa);
		ServidorWS.enviar(jso);
	}
	
	public JSONObject getEstadoMesa(int id) {
		Mesa mesa = (Mesa) this.mesas.get(id);
		return mesa.estado();
	}
	
	public JSONArray getCategorias() {
		return this.jsaCategorias;
	}
	
	public JSONArray getPlatosDeCategoria(String idCategoria) {
		Categoria categoria=(Categoria) this.categorias.get(idCategoria);
		return categoria.getPlatos();
	}
	
	public void platoPreparado(JSONObject jso)throws Exception{
		int idMesa = jso.getInt("idMesa");
		String idPlato = jso.getString("id");
		Mesa mesa=(Mesa)mesas.get(idMesa);
		Vector<PlatoPedido> platos = mesa.getComandaActual().getPlatos();
		for(PlatoPedido pp : platos) {
			if(pp.getPlato().getId().equals(idPlato))
				pp.setPreparado(true);
		}
		mesa.getComandaActual().setPlatos(platos);
		BSONeador.update(mesa.getComandaActual());
	}
	
	public JSONArray getMesas() {
		JSONArray result=new JSONArray();
		Enumeration<Object> eMesas = this.mesas.elements();
		Mesa mesa;
		while (eMesas.hasMoreElements()) {
			mesa=(Mesa) eMesas.nextElement();
			result.put(mesa.toJSONObject());
		}
		return result;
	}
}