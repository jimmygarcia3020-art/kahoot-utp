import { describe, expect, it } from 'vitest';
import { needsProfile, maxBirthDate, percent, validSession, courseStyle, evaluationPayload } from './util';

describe('perfil obligatorio', () => {
  it('una cuenta sin género o fecha de nacimiento debe completar su perfil', () => {
    expect(needsProfile(null)).toBe(false);
    expect(needsProfile({ genero: null, fechaNacimiento: null } as any)).toBe(true);
    expect(needsProfile({ genero: 'FEMENINO' } as any)).toBe(true);
    expect(needsProfile({ genero: 'FEMENINO', fechaNacimiento: '2003-05-14' } as any)).toBe(false);
  });

  it('la fecha máxima de nacimiento es ayer en la hora de Lima', () => {
    expect(maxBirthDate(new Date('2026-10-26T12:00:00-05:00'))).toBe('2026-10-25');
    expect(maxBirthDate(new Date('2026-10-27T02:00:00Z'))).toBe('2026-10-25');
  });
});

describe('utilidades', () => {
  it('porcentaje acotado entre 0 y 100', () => {
    expect(percent(1, 4)).toBe(25);
    expect(percent(5, 0)).toBe(0);
    expect(percent(9, 4)).toBe(100);
  });

  it('sesión expirada no es válida', () => {
    const base = { token: 't', usuarioId: 1, expiresAt: 1000 } as any;
    expect(validSession(base, 500)).toBe(base);
    expect(validSession(base, 2000)).toBeNull();
  });

  it('el estilo del curso se deduce del nombre', () => {
    expect(courseStyle('Python').key).toBe('python');
    expect(courseStyle('Bases de Datos').key).toBe('database');
    expect(courseStyle('Algoritmos').key).toBe('algorithms');
  });

  it('ordenar bloques envía el orden como números', () => {
    const payload = evaluationPayload({ id: 2, tipo: 'ORDENAR_BLOQUES' }, ['3', '1'], Date.now(), false);
    expect(payload.ordenBloques).toEqual([3, 1]);
    expect(payload.alternativaId).toBeNull();
  });
});
