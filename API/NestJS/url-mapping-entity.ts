// src/url-mapping.entity.ts
import { Entity, PrimaryColumn, Column } from 'typeorm';

@Entity()
export class UrlMapping {
  @PrimaryColumn()
  shortCode: string;

  @Column()
  originalUrl: string;
}
