import { describe, expect, it } from 'vitest';
import { createDemo } from './demo';

const memory = () => { const map = new Map<string, string>(); return { getItem: (k: string) => map.get(k) ?? null, setItem: (k: string, v: string) => { map.set(k, v); } } as Storage; };
const base = { correo: 'nueva@utp.edu.pe', nombres: 'Nueva', apellidos: 'Cuenta', contrasena: 'ClaveSegura123' };

describe('adaptador demo', () => {
  it('el registro exige género válido y fecha de nacimiento', async () => {
    const api = createDemo(memory());
    await expect(api('/auth/registro', { method: 'POST', body: { ...base, genero: 'OTRO', fechaNacimiento: '2003-05-14' } })).rejects.toThrow(/género válido/);
    await expect(api('/auth/registro', { method: 'POST', body: { ...base, genero: 'FEMENINO' } })).rejects.toThrow(/fecha de nacimiento/);
    await api('/auth/registro', { method: 'POST', body: { ...base, genero: 'FEMENINO', fechaNacimiento: '2003-05-14' } });
    const login = await api('/auth/login', { method: 'POST', body: { correo: base.correo, contrasena: base.contrasena } });
    expect(login.genero).toBe('FEMENINO');
    expect(login.fechaNacimiento).toBe('2003-05-14');
  });

  it('completar el perfil persiste género y fecha', async () => {
    const api = createDemo(memory());
    expect((await api('/usuarios/1/perfil')).genero).toBeNull();
    const saved = await api('/usuarios/1/perfil', { method: 'PUT', body: { genero: 'MASCULINO', fechaNacimiento: '2000-01-01' } });
    expect(saved.genero).toBe('MASCULINO');
    expect((await api('/usuarios/1/perfil')).fechaNacimiento).toBe('2000-01-01');
  });
});
