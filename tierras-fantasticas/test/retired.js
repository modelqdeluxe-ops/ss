// Productos retirados de la tienda (la ruleta y las monedas con dinero, por las normas de Mojang para servidores).
// El código sigue en la web por si algún día vuelven de forma permitida, así que los tests lo siguen probando con
// estos productos de ejemplo. Este módulo tiene que importarse antes que la web: añade los productos al catálogo.
import products from '../config/products.json' with { type: 'json' };
import retired from './retired-products.json' with { type: 'json' };

for (const p of retired) if (!products.some((x) => x.id === p.id)) products.push(p);
