
import { Entity, PrimaryGeneratedColumn, Column } from 'typeorm';

@Entity()
export class Analytics {
  @PrimaryGeneratedColumn()
  id: number;

  @Column()
  shortCode: string;

  @Column()
  timestamp: string;

  @Column()
  ipAddress: string;
}
