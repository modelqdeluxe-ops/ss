// Imitación mínima de Cloudflare D1 sobre node:sqlite para las pruebas.
import { DatabaseSync } from 'node:sqlite';

class Statement {
  constructor(db, sql, args = []) {
    this.db = db;
    this.sql = sql;
    this.args = args;
  }
  bind(...args) {
    return new Statement(this.db, this.sql, args);
  }
  async first(column) {
    const row = this.db.prepare(this.sql).get(...this.args);
    if (!row) return null;
    return column ? row[column] : { ...row };
  }
  async all() {
    return { results: this.db.prepare(this.sql).all(...this.args).map((r) => ({ ...r })), success: true };
  }
  async run() {
    return this.exec();
  }
  exec() {
    const info = this.db.prepare(this.sql).run(...this.args);
    return { success: true, meta: { changes: Number(info.changes), last_row_id: Number(info.lastInsertRowid) } };
  }
}

export function createD1() {
  const db = new DatabaseSync(':memory:');
  return {
    raw: db,
    prepare: (sql) => new Statement(db, sql),
    // D1 ejecuta el lote en una transacción.
    async batch(statements) {
      db.exec('BEGIN');
      try {
        const out = statements.map((s) => (/^\s*select/i.test(s.sql) ? { results: s.db.prepare(s.sql).all(...s.args) } : s.exec()));
        db.exec('COMMIT');
        return out;
      } catch (err) {
        db.exec('ROLLBACK');
        throw err;
      }
    },
  };
}
